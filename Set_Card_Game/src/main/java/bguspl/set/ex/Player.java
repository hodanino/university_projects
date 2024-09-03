package bguspl.set.ex;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    protected final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate
     * key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * Choises of the player
     */
    protected BlockingQueue<Integer> keyPresses;

    private Dealer dealer;

    public volatile AtomicBoolean dealerCheck;

    public volatile AtomicBoolean pointR;

    public volatile AtomicBoolean penaltyR;

    protected AtomicBoolean aiFreeze;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided
     *               manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        this.keyPresses = new LinkedBlockingQueue<Integer>(env.config.featureSize) {
        };
        this.dealerCheck = new AtomicBoolean(false);
        this.aiFreeze = new AtomicBoolean(!human);
        this.pointR = new AtomicBoolean(false);
        this.penaltyR = new AtomicBoolean(false);
        this.score = 0;
    }

    /**
     * The main player thread of each player starts here (main loop for the player
     * thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human)
            createArtificialIntelligence();

        synchronized (dealer) {
            dealer.notify();
        }

        while (!terminate) {
            synchronized (this) {
                // the thread is waiting penalty or point
                try {
                    this.wait();
                } catch (InterruptedException e) {
                }
            }
            if (!dealerCheck.get()) {
                if (pointR.get()) {
                    point();
                }
                if (penaltyR.get()) {
                    penalty();
                }
                // if theres ai player we need to notify the penaltyR from here if the player
                // gets a penalty or point
                if (aiFreeze.get()) {
                    synchronized (penaltyR) {
                        penaltyR.notify();
                    }
                }
            }
            // needs to check if aiFreeze is true(means that there's an ai player)
            if (aiFreeze.get())
                synchronized (aiFreeze) {
                    aiFreeze.notify();
                }
        }
        if (!human)
            try {
                aiThread.join();
            } catch (InterruptedException ignored) {
            }
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of
     * this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it
     * is not full.
     */
    private void createArtificialIntelligence() {
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                try {
                    if (dealerCheck.get()) {
                        synchronized (aiFreeze) {
                            aiFreeze.wait();
                        }
                    }
                    int randomSlot = (int) (Math.random() * env.config.tableSize);
                    keyPressed(randomSlot);
                } catch (InterruptedException e) {
                }

            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
        try {
            synchronized (penaltyR) {
                penaltyR.notifyAll();
            }
            synchronized (aiFreeze) {
                aiFreeze.notifyAll();
            }
            aiThread.join();
            synchronized (dealer) {
                dealer.notify();
            }
        } catch (Exception e) {
        }
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if (!table.lock.isWriteLocked()) {
            if (!dealerCheck.get()) {
                // conatain this slot -> remove the slot from the ui and keypresses
                if ((keyPresses.contains(slot))) {
                    keyPresses.remove(slot);
                    table.removeToken(this.id, slot);
                    return;
                }
                // insert this slot using offer method to keypresses and ui:
                if (keyPresses.offer(slot)) {
                    table.placeToken(id, slot);
                    // keyPresses.size == 3 -> dealer check
                    if (keyPresses.size() == env.config.featureSize) {
                        dealerCheck.set(true);
                        getChecked();
                    }
                }

            }
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        int ignored = table.countCards(); // this part is just for demonstration in the unitests
        dealerCheck.set(true);
        env.ui.setScore(id, ++score);
        env.ui.setFreeze(this.id, env.config.pointFreezeMillis);
        try {
            Thread.sleep(env.config.pointFreezeMillis);
        } catch (InterruptedException e) {
        }
        env.ui.setFreeze(this.id, 0);
        pointR.set(false);
        dealerCheck.set(false);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        dealerCheck.set(true);
        long time = System.currentTimeMillis() + env.config.penaltyFreezeMillis;
        while (time > System.currentTimeMillis()) {
            env.ui.setFreeze(this.id, time - System.currentTimeMillis());
            try {
                Thread.sleep(env.config.pointFreezeMillis / 2);
            } catch (InterruptedException e) {
            }
        }
        env.ui.setFreeze(this.id, 0);
        penaltyR.set(false);
        dealerCheck.set(false);
    }

    public int score() {
        return score;
    }

    /**
     * convert queue to array of cards, being used from the dealer side
     */
    public int[] keysToCardsArray() {
        int keysArray[] = new int[env.config.featureSize];
        int i = 0;
        for (int key : keyPresses) {
            if (table.slotToCard[key] == null) {
                return null;
            }
            keysArray[i] = table.slotToCard[key];
            i++;
        }
        // double check
        if (keyPresses.size() < env.config.featureSize) {
            return null;
        }
        return keysArray;
    }

    private void getChecked() {
        synchronized (dealer.playerIdsToCheck) {
            dealer.playerIdsToCheck.add(id);
            dealer.playerIdsToCheck.notify();
        }

        synchronized (penaltyR) {
            try {
                penaltyR.wait();
            } catch (InterruptedException e) {
            }
        }
    }
}
