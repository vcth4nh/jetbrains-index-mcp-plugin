'use strict';
const { Shape } = require('./normal');

class Box extends Shape {
    constructor(s) {
        super();
        this.s = s;
    }
    get area() {
        return this.s * this.s;
    }
}

module.exports = { Box };
