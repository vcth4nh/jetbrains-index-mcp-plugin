export abstract class BaseRepo<T> {
    abstract find(id: number): T;
}

export class Repo<T> extends BaseRepo<T> {
    find(id: number): T {
        throw new Error("not implemented");
    }
}
