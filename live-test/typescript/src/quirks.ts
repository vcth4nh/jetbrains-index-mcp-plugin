export function qLambda(x: string): number {
    const fn: (s: string) => number = (s) => Number.parseInt(s, 10);
    return fn(x);
}

export function qFunctionRef(x: string): number {
    const fn = Number.parseInt;
    return fn(x, 10);
}

export function qGenericLambda<T extends string>(x: T): number {
    const fn = (s: T): number => Number.parseInt(s, 10);
    return fn(x);
}

export function qConditionalType<T extends "int" | "float">(mode: T, x: string): number {
    type Fn = T extends "int" ? typeof Number.parseInt : typeof Number.parseFloat;
    const fn = (mode === "int" ? Number.parseInt : Number.parseFloat) as Fn;
    return fn(x, 10);
}

export function qDispatchMap(key: string, x: string): number {
    const dispatch: Record<string, (s: string) => number> = {
        int: (s) => Number.parseInt(s, 10),
        abs: (s) => Math.abs(Number.parseInt(s, 10)),
    };
    return dispatch[key](x);
}

export function qOptional(x?: string): number {
    return x?.length ? Number.parseInt(x, 10) : 0;
}

export function qNonNullAssertion(x: string | undefined): number {
    return Number.parseInt(x!, 10);
}

export function qAsCast(x: unknown): number {
    return Number.parseInt(x as string, 10);
}

export interface Coercer { coerce(x: string): number; }

export const intCoercer: Coercer = {
    coerce(x: string) { return Number.parseInt(x, 10); }
};

export function qInterfaceDispatch(c: Coercer, x: string): number {
    return c.coerce(x);
}

export class TypedCoercer<T extends string> {
    coerce(x: T): number { return Number.parseInt(x, 10); }
}

export function qGenericClass(x: string): number {
    return new TypedCoercer<string>().coerce(x);
}

export type Coerce = (s: string) => number;

export const aliasedCoerce: Coerce = (s) => Number.parseInt(s, 10);

export function qTypeAlias(x: string): number {
    return aliasedCoerce(x);
}
