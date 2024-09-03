package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    protected BlockingQueue<Integer> playerIdsToCheck;

    private Thread[] playersThreads;

    public Object lock;

    private int playerId;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        this.deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.playerIdsToCheck = new LinkedBlockingQueue<Integer>();
        this.playersThreads = new Thread[players.length];
        this.lock = new Object();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        table.lock.writeLock().lock();
        createAndStartThreads();
        while (!shouldFinish()) {
            try {
                Collections.shuffle(deck);
                placeCardsOnTable();
                updateTimerDisplay(true);
                try {
                    table.lock.writeLock().unlock();
                } catch (Exception e) {
                }
                timerLoop();
                try {
                    table.lock.writeLock().lock();
                } catch (Exception e) {
                }

                removeAllCardsFromTable();
            } catch (Exception e) {
            }
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did
     * not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        for (int i = players.length - 1; i >= 0; i--) {
            try {
                synchronized (players[i]) {
                    players[i].terminate();
                    players[i].notify();
                }
                playersThreads[i].join();
            } catch (InterruptedException e) {
            }
        }

        terminate = true;
    }

    /**
     * .
     * Check if the game should be terminated or the game end conditions are met
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks if cards should be removed from the table and removes them.
     * Check if the marked cards are a legal set
     */
    private void removeCardsFromTable() {
        if (!playerIdsToCheck.isEmpty()) {
            synchronized (playerIdsToCheck) {
                playerId = playerIdsToCheck.poll();
            }
            try {
                table.lock.writeLock().lock();
            } catch (Exception e) {
            }
            int[] cards = players[playerId].keysToCardsArray();
            if (cards == null) {
                players[playerId].dealerCheck.set(false);
                synchronized (players[playerId].penaltyR) {
                    players[playerId].penaltyR.notify();
                }
                return;
            }

            // check for legal set
            if (env.util.testSet(cards)) {
                for (int card : cards) {
                    int slot = table.cardToSlot[card];
                    table.removeCard(slot);
                    env.ui.removeTokens(slot);
                    removeTokensFromPlayers(slot); // remove the token from all players
                }
                // player get a point
                players[playerId].pointR.set(true);
                updateTimerDisplay(true);
            }
            // not a legal set
            else {
                players[playerId].penaltyR.set(true);
                for (int card : cards) {
                    int slot = table.cardToSlot[card];
                    env.ui.removeToken(playerId, slot);
                }
                players[playerId].keyPresses.clear();
                try {
                    table.lock.writeLock().unlock();
                } catch (Exception e) {
                }
                players[playerId].dealerCheck.set(false);
                synchronized (players[playerId]) {
                    players[playerId].notify();
                }
            }
            // wake up only player thread, if ai thread the human thread wake him up
            if (!players[playerId].aiFreeze.get()) {
                synchronized (players[playerId].penaltyR) {
                    players[playerId].penaltyR.notify();
                }
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     * Checks if deck isn't empty
     * put on table amount needed
     */
    private void placeCardsOnTable() {
        boolean replaced = false;
        for (int i = 0; i < env.config.tableSize && !deck.isEmpty(); i++) {
            int nextCardIn = deck.get(0);
            if (table.slotToCard[i] == null) {
                table.placeCard(nextCardIn, i);
                deck.remove(0);
                replaced = true;
            }
        }
        // if we replaced any card update the hints if needed
        if (replaced) {
            if (env.config.hints) {
                table.hints();
            }
        }
        // we unlock here after we locked in removeCards function when player got a
        // point
        if (players[playerId].pointR.get()) {
            try {
                table.lock.writeLock().unlock();
            } catch (Exception e) {
            }
            players[playerId].dealerCheck.set(false);
            synchronized (players[playerId]) {
                players[playerId].notify();
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try {
            synchronized (playerIdsToCheck) {
                playerIdsToCheck.wait(50);
            }
        } catch (InterruptedException e) {
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset) {
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        }
        env.ui.setCountdown(Math.max(reshuffleTime - System.currentTimeMillis(), 0),
                reshuffleTime - System.currentTimeMillis() < env.config.endGamePauseMillies);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        env.ui.removeTokens();
        for (int i = 0; i < env.config.tableSize; i++) {
            if (table.slotToCard[i] != null) {
                int card = table.slotToCard[i];
                deck.add(card);
                table.removeCard(table.cardToSlot[card]);
            }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        List<Integer> winners = new LinkedList<>();
        int maxScore = 0;
        for (Player player : players) {
            int score = player.score();
            if (score > maxScore) {
                winners.clear();
                winners.add(player.id);
                maxScore = score;
            } else if (score == maxScore)
                winners.add(player.id);
        }
        env.ui.announceWinner(winners.stream().mapToInt(i -> i).toArray());
        try {
            Thread.sleep(env.config.endGamePauseMillies);
        } catch (Exception w) {
        }
        terminate();
    }

    /**
     * creates all players' threads and start them
     */
    private void createAndStartThreads() {
        for (int i = 0; i < players.length; i++) {
            playersThreads[i] = new Thread(players[i]);
            env.logger.info("thread " + players[i].id + "init");
            try {
                synchronized (this) {
                    playersThreads[i].start();
                    this.wait(); // waiting for the player to notify
                }
            } catch (InterruptedException e) {
            }
        }
    }

    private void removeTokensFromPlayers(int slot) {
        for (Player player : players) {
            player.keyPresses.remove(slot);
        }
    }
}
