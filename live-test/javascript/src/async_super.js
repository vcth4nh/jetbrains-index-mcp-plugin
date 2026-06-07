'use strict';

class AsyncBase {
    async fetch() { return "base"; }
}

class AsyncChild extends AsyncBase {
    async fetch() { return "child"; }
}

module.exports = { AsyncBase, AsyncChild };
