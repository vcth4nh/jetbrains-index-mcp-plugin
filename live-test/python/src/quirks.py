"""Language-quirk patterns for the live MCP test harness.

Each function exercises a Python-specific indirection or rebinding pattern.
The target functions are deliberately neutral — the patterns themselves are
what the navigation tools must resolve.
"""
import functools
import operator


def quirk_name_rebinding(x: str) -> int:
    fn = int
    return fn(x)


def quirk_getattr_module(name: str) -> int:
    fn = getattr(operator, "abs")
    return fn(int(name))


def quirk_functools_partial(x: str) -> int:
    coerce = functools.partial(int)
    return coerce(x)


def quirk_dict_dispatch(key: str, x: str) -> int:
    dispatch = {"int": int, "abs": lambda v: abs(int(v))}
    return dispatch[key](x)


def quirk_lambda_wrap(x: str) -> int:
    coerce = lambda v: int(v)
    return coerce(x)


def quirk_list_indexing(x: str) -> int:
    funcs = [int, str, float]
    return funcs[0](x)


def quirk_conditional_expr(x: str, use_int: bool) -> int | float:
    fn = int if use_int else float
    return fn(x)


def quirk_star_import_simulation(x: str) -> int:
    from operator import abs as a
    return a(int(x))


def quirk_decorator_wrap(x: str) -> int:
    def with_logging(fn):
        @functools.wraps(fn)
        def wrapper(*args, **kwargs):
            return fn(*args, **kwargs)
        return wrapper
    wrapped = with_logging(int)
    return wrapped(x)


def quirk_class_method(x: str) -> int:
    class Coercer:
        def coerce(self, raw: str) -> int:
            return int(raw)
    return Coercer().coerce(x)


def quirk_walrus(x: str) -> int:
    if (result := int(x)):
        return result
    return 0


def quirk_unpacking(x: str) -> int:
    fn, *_ = [int, float]
    return fn(x)


def quirk_nested_return(x: str) -> int:
    def get_coercer():
        return int
    return get_coercer()(x)


def quirk_map_filter(items: list[str]) -> list[int]:
    return list(map(int, items))


def quirk_reduce(values: list[str]) -> int:
    return functools.reduce(lambda acc, v: acc + int(v), values, 0)


def quirk_chained_getattr(x: str) -> int:
    fn = getattr(getattr(operator, "abs"), "__call__")
    return fn(int(x))


def quirk_multiple_assignment(x: str) -> int:
    a = b = int
    return a(x) + b(x)
