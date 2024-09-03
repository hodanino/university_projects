package bgu.spl.net.srv;

import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl<T> implements Connections<T> {
    
    private ConcurrentHashMap<Integer, BlockingConnectionHandler<T>> ids_login = new ConcurrentHashMap<>();

    public void connect(int connectionId, BlockingConnectionHandler<T> handler) {
        ids_login.put(connectionId, handler);
    }

    public boolean send(int connectionId, T msg) {
        ids_login.get(connectionId).send(msg);
        return true;
    }

    public void disconnect(int connectionId) {
        ids_login.remove(connectionId);
    }

}