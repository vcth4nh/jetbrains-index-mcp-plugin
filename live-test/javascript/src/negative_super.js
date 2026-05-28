'use strict';

class Standalone {
    compute() { return "standalone"; }
}

function standaloneFn() { return "standalone"; }

module.exports = { Standalone, standaloneFn };
