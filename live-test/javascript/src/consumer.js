'use strict';
const { Circle, makeDefaultShapes } = require('./normal');

function use() {
    return new Circle(2).area() + makeDefaultShapes().length;
}

module.exports = { use };
