export interface AsyncFetcher {
    fetch(): Promise<string>;
}

export class AsyncImpl implements AsyncFetcher {
    async fetch(): Promise<string> { return "fetched"; }
}
