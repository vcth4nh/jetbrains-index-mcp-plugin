export interface WithValue {
    value: string;
}

export class WithSetter implements WithValue {
    private _v = "";
    set value(v: string) { this._v = v.toUpperCase(); }
    get value(): string { return this._v; }
}
