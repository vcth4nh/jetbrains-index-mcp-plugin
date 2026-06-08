# Heavy tier — review (46 new probes, UNBLESSED)

Each probe shows its input params and the **actual IDE output** captured this session. Existing rows all still PASS (no drift). Nothing blessed.


---
## S1 — TypeScript: enums / namespaces / decorators  (20 probes)


**New fixture `src/enums.ts`:**
```ts
export enum Direction {
    North,
    East,
    South,
    West,
}

export enum Color {
    Red = "red",
    Green = "green",
    Blue = "blue",
}

export function opposite(d: Direction): Direction {
    if (d === Direction.North) return Direction.South;
    if (d === Direction.South) return Direction.North;
    if (d === Direction.East) return Direction.West;
    return Direction.East;
}

export function isWarm(c: Color): boolean {
    return c === Color.Red || c === Color.Green;
}
```


**New fixture `src/namespaces.ts`:**
```ts
export namespace Geometry {
    export class Point {
        constructor(public readonly x: number, public readonly y: number) {}

        distanceTo(other: Point): number {
            return Math.hypot(this.x - other.x, this.y - other.y);
        }
    }

    export function origin(): Point {
        return new Point(0, 0);
    }
}

// declaration merging augments Geometry
export namespace Geometry {
    export function unit(): Point {
        return new Point(1, 0);
    }
}

export function span(): number {
    return Geometry.origin().distanceTo(Geometry.unit());
}
```


**New fixture `src/decorators.ts`:**
```ts
export function traced(target: Function, context: any): void {
    void target;
    void context;
}

export class Service {
    @traced
    greet(name: string): string {
        return `hello ${name}`;
    }

    @traced
    farewell(name: string): string {
        return `bye ${name}`;
    }
}
```


### `def-Direction.North-enum` · ide_find_definition
input: `{"file": "src/enums.ts", "line": 15, "column": 25}`
output:
```json
{
 "column": 5,
 "enclosingScope": null,
 "file": "src/enums.ts",
 "kind": "READONLY_FIELD",
 "line": 2,
 "name": "North",
 "qualifiedName": "Direction.North"
}
```

### `usage-Direction.North-enum` · ide_find_usages
input: `{"file": "src/enums.ts", "line": 2, "column": 5}`
output:
```json
{
 "totalCount": 2,
 "usages": [
  {
   "column": 15,
   "enclosingScope": [
    "opposite"
   ],
   "file": "src/enums.ts",
   "line": 15,
   "usageType": "REFERENCE"
  },
  {
   "column": 39,
   "enclosingScope": [
    "opposite"
   ],
   "file": "src/enums.ts",
   "line": 16,
   "usageType": "REFERENCE"
  }
 ]
}
```

### `usage-Direction-enum` · ide_find_usages
input: `{"file": "src/enums.ts", "line": 1, "column": 13}`
output:
```json
{
 "totalCount": 9,
 "usages": [
  {
   "column": 29,
   "enclosingScope": [
    "opposite",
    "d"
   ],
   "file": "src/enums.ts",
   "line": 14,
   "usageType": "REFERENCE"
  },
  {
   "column": 41,
   "enclosingScope": [
    "opposite"
   ],
   "file": "src/enums.ts",
   "line": 14,
   "usageType": "REFERENCE"
  },
  {
   "column": 15,
   "enclosingScope": [
    "opposite"
   ],
   "file": "src/enums.ts",
   "line": 15,
   "usageType": "REFERENCE"
  },
  {
   "column": 39,
   "enclosingScope": [
    "opposite"
   ],
   "file": "src/enums.ts",
   "line": 15,
   "usageType": "REFERENCE"
  },
  {
   "column": 15,
   "enclosingScope": [
    "opposite"
   ],
   "file": "src/enums.ts",
   "line": 16,
   "usageType": "REFERENCE"
  },
  {
   "column": 39,
   "enclosingScope": [
    "opposite"
   ],
   "file": "src/enums.ts",
   "line": 16,
   "usageType": "REFERENCE"
  },
  {
   "column": 15,
   "enclosingScope": [
    "opposite"
   ],
   "file": "src/enums.ts",
   "line": 17,
   "usageType": "REFERENCE"
  },
  {
   "column": 38,
   "enclosingScope": [
    "opposite"
   ],
   "file": "src/enums.ts",
   "line": 17,
   "usageType": "REFERENCE"
  },
  {
   "column": 12,
   "enclosingScope": [
    "opposite"
   ],
   "file": "src/enums.ts",
   "line": 18,
   "usageType": "REFERENCE"
  }
 ]
}
```

### `def-Color.Red-enum` · ide_find_definition
input: `{"file": "src/enums.ts", "line": 22, "column": 24}`
output:
```json
{
 "column": 5,
 "enclosingScope": null,
 "file": "src/enums.ts",
 "kind": "READONLY_FIELD",
 "line": 9,
 "name": "Red",
 "qualifiedName": "Color.Red"
}
```

### `find-class-Direction` · ide_find_class
input: `{"query": "Direction"}`
output:
```json
{
 "classes": [
  {
   "column": 13,
   "file": "src/enums.ts",
   "kind": "ENUM",
   "line": 1,
   "name": "Direction",
   "qualifiedName": "Direction"
  }
 ],
 "query": "Direction",
 "totalCount": 1
}
```

### `find-symbol-Direction` · ide_find_symbol
input: `{"query": "Direction"}`
output:
```json
{
 "query": "Direction",
 "symbols": [
  {
   "column": 13,
   "file": "src/enums.ts",
   "kind": "ENUM",
   "line": 1,
   "name": "Direction",
   "qualifiedName": "Direction"
  }
 ],
 "totalCount": 1
}
```

### `hier-type-Direction` · ide_type_hierarchy  ⚠️ **FLAGGED (HARD — Number×3 supertype; confirm Type Hierarchy in WebStorm)**
input: `{"file": "src/enums.ts", "line": 1, "column": 13}`
output:
```json
{
 "element": {
  "column": 13,
  "enclosingScope": null,
  "file": "src/enums.ts",
  "kind": "ENUM",
  "line": 1,
  "name": "Direction",
  "qualifiedName": "Direction",
  "supertypes": null
 },
 "subtypes": [],
 "supertypes": [
  {
   "column": 11,
   "enclosingScope": null,
   "file": "${WEBSTORM_JS_STUBS}/lib.es2020.number.d.ts",
   "kind": "INTERFACE",
   "line": 21,
   "name": "Number",
   "qualifiedName": "Number",
   "supertypes": null
  },
  {
   "column": 11,
   "enclosingScope": null,
   "file": "${WEBSTORM_JS_STUBS}/lib.es5.d.ts",
   "kind": "INTERFACE",
   "line": 559,
   "name": "Number",
   "qualifiedName": "Number",
   "supertypes": null
  },
  {
   "column": 11,
   "enclosingScope": null,
   "file": "${WEBSTORM_JS_STUBS}/lib.es5.d.ts",
   "kind": "INTERFACE",
   "line": 4572,
   "name": "Number",
   "qualifiedName": "Number",
   "supertypes": null
  }
 ]
}
```

### `file-structure-Enums` · ide_file_structure
input: `{"file": "src/enums.ts"}`
output (file_structure):
```
enums.ts

export Direction (line 1)
  North: Direction.North (line 2)
  East: Direction.East (line 3)
  South: Direction.South (line 4)
  West: Direction.West (line 5)
export Color (line 8)
  Red: Color.Red (line 9)
  Green: Color.Green (line 10)
  Blue: Color.Blue (line 11)
export opposite(d: Direction): Direction (line 14)
export isWarm(c: Color): boolean (line 21)
```

### `def-Geometry.origin-call` · ide_find_definition
input: `{"file": "src/namespaces.ts", "line": 23, "column": 21}`
output:
```json
{
 "column": 21,
 "enclosingScope": null,
 "file": "src/namespaces.ts",
 "kind": "FUNCTION",
 "line": 10,
 "name": "origin",
 "qualifiedName": "Geometry.origin"
}
```

### `usage-Geometry-namespace` · ide_find_usages
input: `{"file": "src/namespaces.ts", "line": 1, "column": 18}`
output:
```json
{
 "totalCount": 2,
 "usages": [
  {
   "column": 12,
   "enclosingScope": [
    "span"
   ],
   "file": "src/namespaces.ts",
   "line": 23,
   "usageType": "REFERENCE"
  },
  {
   "column": 41,
   "enclosingScope": [
    "span"
   ],
   "file": "src/namespaces.ts",
   "line": 23,
   "usageType": "REFERENCE"
  }
 ]
}
```

### `usage-Point-namespace` · ide_find_usages
input: `{"file": "src/namespaces.ts", "line": 2, "column": 18}`
output:
```json
{
 "totalCount": 5,
 "usages": [
  {
   "column": 27,
   "enclosingScope": [
    "Geometry",
    "Point",
    "distanceTo",
    "other"
   ],
   "file": "src/namespaces.ts",
   "line": 5,
   "usageType": "REFERENCE"
  },
  {
   "column": 31,
   "enclosingScope": [
    "Geometry",
    "origin"
   ],
   "file": "src/namespaces.ts",
   "line": 10,
   "usageType": "REFERENCE"
  },
  {
   "column": 20,
   "enclosingScope": [
    "Geometry",
    "origin"
   ],
   "file": "src/namespaces.ts",
   "line": 11,
   "usageType": "REFERENCE"
  },
  {
   "column": 29,
   "enclosingScope": [
    "Geometry",
    "unit"
   ],
   "file": "src/namespaces.ts",
   "line": 17,
   "usageType": "REFERENCE"
  },
  {
   "column": 20,
   "enclosingScope": [
    "Geometry",
    "unit"
   ],
   "file": "src/namespaces.ts",
   "line": 18,
   "usageType": "REFERENCE"
  }
 ]
}
```

### `find-class-Point` · ide_find_class
input: `{"query": "Point"}`
output:
```json
{
 "classes": [
  {
   "column": 18,
   "file": "src/namespaces.ts",
   "kind": "CLASS",
   "line": 2,
   "name": "Point",
   "qualifiedName": "Geometry.Point"
  }
 ],
 "query": "Point",
 "totalCount": 1
}
```

### `find-symbol-Geometry` · ide_find_symbol
input: `{"query": "Geometry"}`
output:
```json
{
 "query": "Geometry",
 "symbols": [
  {
   "column": 18,
   "file": "src/namespaces.ts",
   "kind": "NAMESPACE",
   "line": 1,
   "name": "Geometry",
   "qualifiedName": "Geometry"
  },
  {
   "column": 18,
   "file": "src/namespaces.ts",
   "kind": "NAMESPACE",
   "line": 16,
   "name": "Geometry",
   "qualifiedName": "Geometry"
  }
 ],
 "totalCount": 2
}
```

### `find-symbol-origin` · ide_find_symbol
input: `{"query": "origin"}`
output:
```json
{
 "query": "origin",
 "symbols": [
  {
   "column": 21,
   "file": "src/namespaces.ts",
   "kind": "FUNCTION",
   "line": 10,
   "name": "origin",
   "qualifiedName": "Geometry.origin"
  }
 ],
 "totalCount": 1
}
```

### `file-structure-Namespaces` · ide_file_structure
input: `{"file": "src/namespaces.ts"}`
output (file_structure):
```
namespaces.ts

export Geometry (line 1)
  export Point (line 2)
    constructor(x: number, y: number) (line 3)
    public readonly x: number (line 3)
    public readonly y: number (line 3)
    distanceTo(other: Point): number (line 5)
  export origin(): Point (line 10)
export Geometry (line 16)
  export unit(): Point (line 17)
export span(): number (line 22)
```

### `hier-callee-span` · ide_call_hierarchy
input: `{"file": "src/namespaces.ts", "line": 22, "column": 17, "direction": "callees", "maxDepth": 2}`
output:
```json
{
 "calls": [
  {
   "children": [
    {
     "children": null,
     "column": 5,
     "enclosingScope": null,
     "file": "${WEBSTORM_JS_STUBS}/lib.es2015.core.d.ts",
     "kind": "METHOD",
     "line": 191,
     "name": "Math.hypot(number)",
     "qualifiedName": "Math.hypot"
    }
   ],
   "column": 9,
   "enclosingScope": null,
   "file": "src/namespaces.ts",
   "kind": "METHOD",
   "line": 5,
   "name": "Point.distanceTo(Point)",
   "qualifiedName": "Geometry.Point.distanceTo"
  },
  {
   "children": [
    {
     "children": null,
     "column": 18,
     "enclosingScope": null,
     "file": "src/namespaces.ts",
     "kind": "CLASS",
     "line": 2,
     "name": "Point",
     "qualifiedName": "Geometry.Point"
    }
   ],
   "column": 21,
   "enclosingScope": null,
   "file": "src/namespaces.ts",
   "kind": "FUNCTION",
   "line": 10,
   "name": "origin()",
   "qualifiedName": "Geometry.origin"
  },
  {
   "children": [
    {
     "children": null,
     "column": 18,
     "enclosingScope": null,
     "file": "src/namespaces.ts",
     "kind": "CLASS",
     "line": 2,
     "name": "Point",
     "qualifiedName": "Geometry.Point"
    }
   ],
   "column": 21,
   "enclosingScope": null,
   "file": "src/namespaces.ts",
   "kind": "FUNCTION",
   "line": 17,
   "name": "unit()",
   "qualifiedName": "Geometry.unit"
  }
 ],
 "element": {
  "children": null,
  "column": 17,
  "enclosingScope": null,
  "file": "src/namespaces.ts",
  "kind": "FUNCTION",
  "line": 22,
  "name": "span()",
  "qualifiedName": "span"
 }
}
```

### `def-traced-decorator` · ide_find_definition
input: `{"file": "src/decorators.ts", "line": 7, "column": 6}`
output:
```json
{
 "column": 17,
 "enclosingScope": null,
 "file": "src/decorators.ts",
 "kind": "FUNCTION",
 "line": 1,
 "name": "traced",
 "qualifiedName": "traced"
}
```

### `usage-traced-decorator` · ide_find_usages
input: `{"file": "src/decorators.ts", "line": 1, "column": 17}`
output:
```json
{
 "totalCount": 2,
 "usages": [
  {
   "column": 6,
   "enclosingScope": [
    "Service",
    "greet"
   ],
   "file": "src/decorators.ts",
   "line": 7,
   "usageType": "REFERENCE"
  },
  {
   "column": 6,
   "enclosingScope": [
    "Service",
    "farewell"
   ],
   "file": "src/decorators.ts",
   "line": 12,
   "usageType": "REFERENCE"
  }
 ]
}
```

### `find-symbol-traced` · ide_find_symbol
input: `{"query": "traced"}`
output:
```json
{
 "query": "traced",
 "symbols": [
  {
   "column": 17,
   "file": "src/decorators.ts",
   "kind": "FUNCTION",
   "line": 1,
   "name": "traced",
   "qualifiedName": "traced"
  }
 ],
 "totalCount": 1
}
```

### `file-structure-Decorators` · ide_file_structure
input: `{"file": "src/decorators.ts"}`
output (file_structure):
```
decorators.ts

export traced(target: Function, context: any): void (line 1)
export Service (line 6)
  greet(name: string): string (line 8)
  farewell(name: string): string (line 13)
```

---
## S2 — Rust: macros  (7 probes)


**New fixture `src/macros.rs`:**
```rust
macro_rules! square {
    ($x:expr) => {
        $x * $x
    };
}

#[derive(Debug, Clone, PartialEq)]
pub struct Point {
    pub x: i32,
    pub y: i32,
}

impl Point {
    pub fn origin() -> Self {
        Point { x: 0, y: 0 }
    }
}

pub fn demo() -> i32 {
    let p = Point::origin();
    let q = p.clone();
    let _eq = p == q;
    square!(p.x) + square!(q.y)
}
```


### `def-square-macro` · ide_find_definition
input: `{"file": "src/macros.rs", "line": 23, "column": 5}`
output:
```json
{
 "column": 14,
 "enclosingScope": null,
 "file": "src/macros.rs",
 "kind": "MACRO",
 "line": 1,
 "name": "square",
 "qualifiedName": "crate::square"
}
```

### `usage-square-macro` · ide_find_usages
input: `{"file": "src/macros.rs", "line": 1, "column": 14}`
output:
```json
{
 "totalCount": 2,
 "usages": [
  {
   "column": 5,
   "enclosingScope": [
    "demo"
   ],
   "file": "src/macros.rs",
   "line": 23,
   "usageType": "REFERENCE"
  },
  {
   "column": 20,
   "enclosingScope": [
    "demo"
   ],
   "file": "src/macros.rs",
   "line": 23,
   "usageType": "REFERENCE"
  }
 ]
}
```

### `find-symbol-square` · ide_find_symbol
input: `{"query": "square"}`
output:
```json
{
 "query": "square",
 "symbols": [
  {
   "column": 14,
   "file": "src/macros.rs",
   "kind": "MACRO",
   "line": 1,
   "name": "square",
   "qualifiedName": "crate::square"
  },
  {
   "column": 12,
   "file": "src/normal.rs",
   "kind": "STRUCT",
   "line": 54,
   "name": "Square",
   "qualifiedName": "crate::Square"
  }
 ],
 "totalCount": 2
}
```

### `def-Point.clone-derived` · ide_find_definition
input: `{"file": "src/macros.rs", "line": 21, "column": 15}`
output:
```json
{
 "column": 8,
 "enclosingScope": null,
 "file": "${RUST_STDLIB}/core/src/clone.rs",
 "kind": "METHOD",
 "line": 236,
 "name": "clone",
 "qualifiedName": "crate::Clone::clone"
}
```

### `usage-Point-derived` · ide_find_usages
input: `{"file": "src/macros.rs", "line": 8, "column": 12}`
output:
```json
{
 "totalCount": 3,
 "usages": [
  {
   "column": 6,
   "enclosingScope": [],
   "file": "src/macros.rs",
   "line": 13,
   "usageType": "REFERENCE"
  },
  {
   "column": 9,
   "enclosingScope": [
    "origin"
   ],
   "file": "src/macros.rs",
   "line": 15,
   "usageType": "REFERENCE"
  },
  {
   "column": 13,
   "enclosingScope": [
    "demo"
   ],
   "file": "src/macros.rs",
   "line": 20,
   "usageType": "REFERENCE"
  }
 ]
}
```

### `find-class-Point` · ide_find_class
input: `{"query": "Point"}`
output:
```json
{
 "classes": [
  {
   "column": 12,
   "file": "src/macros.rs",
   "kind": "STRUCT",
   "line": 8,
   "name": "Point",
   "qualifiedName": "crate::Point"
  }
 ],
 "query": "Point",
 "totalCount": 1
}
```

### `file-structure-Macros` · ide_file_structure
input: `{"file": "src/macros.rs"}`
output (file_structure):
```
macros.rs

square (line 1)
pub Point (line 8)
  pub x: i32 (line 9)
  pub y: i32 (line 10)
Point (line 13)
  pub origin() -> Self (line 14)
pub demo() -> i32 (line 19)
```

---
## S3a — Python: dataclass  (4 probes)


### `hier-type-ChildDC-dataclass` · ide_type_hierarchy
input: `{"file": "src/dataclass_super.py", "line": 14, "column": 7}`
output:
```json
{
 "element": {
  "column": 7,
  "enclosingScope": null,
  "file": "src/dataclass_super.py",
  "kind": "CLASS",
  "line": 14,
  "name": "ChildDC(ParentDC)",
  "qualifiedName": "dataclass_super.ChildDC",
  "supertypes": null
 },
 "subtypes": [],
 "supertypes": [
  {
   "column": 7,
   "enclosingScope": null,
   "file": "src/dataclass_super.py",
   "kind": "CLASS",
   "line": 6,
   "name": "ParentDC",
   "qualifiedName": "dataclass_super.ParentDC",
   "supertypes": [
    {
     "column": 7,
     "enclosingScope": null,
     "file": "${PYCHARM_TYPESHED}/stdlib/builtins.pyi",
     "kind": "CLASS",
     "line": 109,
     "name": "object",
     "qualifiedName": "object",
     "supertypes": null
    }
   ]
  }
 ]
}
```

### `usage-ParentDC-dataclass` · ide_find_usages
input: `{"file": "src/dataclass_super.py", "line": 6, "column": 7}`
output:
```json
{
 "totalCount": 1,
 "usages": [
  {
   "column": 15,
   "enclosingScope": [
    "ChildDC"
   ],
   "file": "src/dataclass_super.py",
   "line": 14,
   "usageType": "REFERENCE"
  }
 ]
}
```

### `usage-name-field-dataclass` · ide_find_usages  ⚠️ **FLAGGED (SOFT — 3 incl. decl line 7; confirm Find Usages in PyCharm)**
input: `{"file": "src/dataclass_super.py", "line": 7, "column": 5}`
output:
```json
{
 "totalCount": 3,
 "usages": [
  {
   "column": 5,
   "enclosingScope": [
    "ParentDC"
   ],
   "file": "src/dataclass_super.py",
   "line": 7,
   "usageType": "REFERENCE"
  },
  {
   "column": 14,
   "enclosingScope": [
    "ParentDC",
    "__post_init__"
   ],
   "file": "src/dataclass_super.py",
   "line": 10,
   "usageType": "REFERENCE"
  },
  {
   "column": 21,
   "enclosingScope": [
    "ParentDC",
    "__post_init__"
   ],
   "file": "src/dataclass_super.py",
   "line": 10,
   "usageType": "REFERENCE"
  }
 ]
}
```

### `file-structure-dataclass_super` · ide_file_structure
input: `{"file": "src/dataclass_super.py"}`
output (file_structure):
```
dataclass_super.py

ParentDC (line 6)
  __post_init__(self) (line 9)
  name (line 10)
ChildDC(ParentDC) (line 14)
  __post_init__(self) (line 17)
  extra (line 19)
```

---
## S3b — Kotlin: companion  (4 probes)


### `hier-type-CompChild-companion` · ide_type_hierarchy
input: `{"file": "src/main/kotlin/demo/CompanionSuper.kt", "line": 18, "column": 7}`
output:
```json
{
 "element": {
  "column": 7,
  "enclosingScope": null,
  "file": "src/main/kotlin/demo/CompanionSuper.kt",
  "kind": "CLASS",
  "line": 18,
  "name": "CompChild",
  "qualifiedName": "demo.CompChild",
  "supertypes": null
 },
 "subtypes": [],
 "supertypes": [
  {
   "column": 16,
   "enclosingScope": null,
   "file": "src/main/kotlin/demo/CompanionSuper.kt",
   "kind": "ABSTRACT_CLASS",
   "line": 11,
   "name": "CompParent",
   "qualifiedName": "demo.CompParent",
   "supertypes": [
    {
     "column": 19,
     "enclosingScope": null,
     "file": "${KOTLIN_STDLIB}.jar!/kotlin/kotlin.kotlin_builtins",
     "kind": "CLASS",
     "line": 39,
     "name": "Any",
     "qualifiedName": "kotlin.Any",
     "supertypes": null
    }
   ]
  }
 ]
}
```

### `hier-type-CompFactory` · ide_type_hierarchy
input: `{"file": "src/main/kotlin/demo/CompanionSuper.kt", "line": 3, "column": 11}`
output:
```json
{
 "element": {
  "column": 11,
  "enclosingScope": null,
  "file": "src/main/kotlin/demo/CompanionSuper.kt",
  "kind": "INTERFACE",
  "line": 3,
  "name": "CompFactory",
  "qualifiedName": "demo.CompFactory",
  "supertypes": null
 },
 "subtypes": [
  {
   "column": 5,
   "enclosingScope": null,
   "file": "src/main/kotlin/demo/CompanionSuper.kt",
   "kind": "OBJECT",
   "line": 12,
   "name": "Companion",
   "qualifiedName": "demo.CompParent.Companion",
   "supertypes": null
  },
  {
   "column": 5,
   "enclosingScope": null,
   "file": "src/main/kotlin/demo/CompanionSuper.kt",
   "kind": "OBJECT",
   "line": 19,
   "name": "Companion",
   "qualifiedName": "demo.CompChild.Companion",
   "supertypes": null
  }
 ],
 "supertypes": []
}
```

### `usage-CompParent-companion` · ide_find_usages
input: `{"file": "src/main/kotlin/demo/CompanionSuper.kt", "line": 11, "column": 16}`
output:
```json
{
 "totalCount": 1,
 "usages": [
  {
   "column": 19,
   "enclosingScope": [
    "CompChild"
   ],
   "file": "src/main/kotlin/demo/CompanionSuper.kt",
   "line": 18,
   "usageType": "REFERENCE"
  }
 ]
}
```

### `file-structure-CompanionSuper` · ide_file_structure
input: `{"file": "src/main/kotlin/demo/CompanionSuper.kt"}`
output (file_structure):
```
CompanionSuper.kt

CompFactory (line 3)
  make(): String (line 4)
KindBearer (line 7)
  KIND: String (line 8)
abstract CompParent (line 11)
  companion companion object (line 12)
    override make(): String (line 13)
    override KIND: String (line 14)
CompChild (line 18)
  companion companion object (line 19)
    override make(): String (line 20)
    override KIND: String (line 21)
```

---
## S3c — PHP: trait + enum  (7 probes)


### `usage-RequiresImpl-trait` · ide_find_usages
input: `{"file": "src/AbstractTraitSuper.php", "line": 4, "column": 7}`
output:
```json
{
 "totalCount": 1,
 "usages": [
  {
   "column": 9,
   "enclosingScope": [
    "Demo",
    "Implementer",
    "RequiresImpl"
   ],
   "file": "src/AbstractTraitSuper.php",
   "line": 9,
   "usageType": "REFERENCE"
  }
 ]
}
```

### `hier-type-Implementer-trait` · ide_type_hierarchy
input: `{"file": "src/AbstractTraitSuper.php", "line": 8, "column": 7}`
output:
```json
{
 "element": {
  "column": 7,
  "enclosingScope": null,
  "file": "src/AbstractTraitSuper.php",
  "kind": "CLASS",
  "line": 8,
  "name": "Implementer",
  "qualifiedName": "\\Demo\\Implementer",
  "supertypes": null
 },
 "subtypes": [],
 "supertypes": [
  {
   "column": 7,
   "enclosingScope": null,
   "file": "src/AbstractTraitSuper.php",
   "kind": "TRAIT",
   "line": 4,
   "name": "RequiresImpl",
   "qualifiedName": "\\Demo\\RequiresImpl",
   "supertypes": null
  }
 ]
}
```

### `file-structure-AbstractTraitSuper` · ide_file_structure
input: `{"file": "src/AbstractTraitSuper.php"}`
output (file_structure):
```
AbstractTraitSuper.php

public RequiresImpl (line 4)
  abstract public required(): string (line 5)
public Implementer (line 8)
  public required(): string ↑RequiresImpl (line 11)
```

### `hier-type-Severity-enum` · ide_type_hierarchy
input: `{"file": "src/EnumSuper.php", "line": 8, "column": 6}`
output:
```json
{
 "element": {
  "column": 6,
  "enclosingScope": null,
  "file": "src/EnumSuper.php",
  "kind": "ENUM",
  "line": 8,
  "name": "Severity",
  "qualifiedName": "\\Demo\\Severity",
  "supertypes": null
 },
 "subtypes": [],
 "supertypes": [
  {
   "column": 11,
   "enclosingScope": null,
   "file": "${PHP_STUBS}.jar!/stubs/Core/Core_c.php",
   "kind": "INTERFACE",
   "line": 951,
   "name": "BackedEnum",
   "qualifiedName": "\\BackedEnum",
   "supertypes": [
    {
     "column": 11,
     "enclosingScope": null,
     "file": "${PHP_STUBS}.jar!/stubs/Core/Core_c.php",
     "kind": "INTERFACE",
     "line": 937,
     "name": "UnitEnum",
     "qualifiedName": "\\UnitEnum",
     "supertypes": null
    }
   ]
  },
  {
   "column": 11,
   "enclosingScope": null,
   "file": "src/EnumSuper.php",
   "kind": "INTERFACE",
   "line": 4,
   "name": "Labeled",
   "qualifiedName": "\\Demo\\Labeled",
   "supertypes": null
  }
 ]
}
```

### `usage-Severity.Low-enum` · ide_find_usages
input: `{"file": "src/EnumSuper.php", "line": 9, "column": 10}`
output:
```json
{
 "totalCount": 1,
 "usages": [
  {
   "column": 13,
   "enclosingScope": [
    "Demo",
    "Severity",
    "label"
   ],
   "file": "src/EnumSuper.php",
   "line": 14,
   "usageType": "REFERENCE"
  }
 ]
}
```

### `usage-Labeled-interface` · ide_find_usages
input: `{"file": "src/EnumSuper.php", "line": 4, "column": 11}`
output:
```json
{
 "totalCount": 1,
 "usages": [
  {
   "column": 34,
   "enclosingScope": [
    "Demo",
    "Severity"
   ],
   "file": "src/EnumSuper.php",
   "line": 8,
   "usageType": "REFERENCE"
  }
 ]
}
```

### `file-structure-EnumSuper` · ide_file_structure
input: `{"file": "src/EnumSuper.php"}`
output (file_structure):
```
EnumSuper.php

abstract public Labeled (line 4)
  public label(): string (line 5)
public final Severity (line 8)
  public static final Low: Severity = "low" (line 9)
  public static final High: Severity = "high" (line 10)
  public label(): string ↑Labeled (line 12)
```

---
## S4 — JavaScript: quirks dispatch  (4 probes)


### `def-Quirks-computed-key` · ide_find_definition
input: `{"file": "src/quirks.js", "line": 11, "column": 19}`
output:
```json
{
 "column": 20,
 "enclosingScope": null,
 "file": "src/quirks.js",
 "kind": "PARAMETER",
 "line": 10,
 "name": "name",
 "qualifiedName": "name"
}
```

### `def-Quirks-cond-parseint` · ide_find_definition
input: `{"file": "src/quirks.js", "line": 21, "column": 27}`
output:
```json
{
 "column": 5,
 "enclosingScope": null,
 "file": "${WEBSTORM_JS_STUBS}/lib.es2015.core.d.ts",
 "kind": "METHOD",
 "line": 276,
 "name": "parseInt",
 "qualifiedName": "NumberConstructor.parseInt"
}
```

### `def-Quirks-bind-parseint` · ide_find_definition
input: `{"file": "src/quirks.js", "line": 48, "column": 19}`
output:
```json
{
 "column": 5,
 "enclosingScope": null,
 "file": "${WEBSTORM_JS_STUBS}/lib.es2015.core.d.ts",
 "kind": "METHOD",
 "line": 276,
 "name": "parseInt",
 "qualifiedName": "NumberConstructor.parseInt"
}
```

### `def-Quirks-bind-call` · ide_find_definition
input: `{"file": "src/quirks.js", "line": 48, "column": 28}`
output:
```json
{
 "column": 5,
 "enclosingScope": null,
 "file": "${WEBSTORM_JS_STUBS}/lib.es5.d.ts",
 "kind": "METHOD",
 "line": 288,
 "name": "call",
 "qualifiedName": "Function.call"
}
```