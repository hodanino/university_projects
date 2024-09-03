package bgu.spl.net.srv;

public interface Connections<T> {

    void connect(int connectionId, BlockingConnectionHandler<T> blockingConnectionHandler);

    boolean send(int connectionId, T msg);

    void disconnect(int connectionId);
}
