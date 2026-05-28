package demo;

abstract class BaseRepo<T> {
    abstract T find(int id);
}

class Repo<T> extends BaseRepo<T> {
    @Override
    T find(int id) {
        return null;
    }
}
