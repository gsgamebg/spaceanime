const test = require('node:test');
const assert = require('node:assert/strict');
const { calculateXp } = require('./xp');
test('XP multipliers use exact integer arithmetic', () => {
 assert.equal(calculateXp(100,null),100);
 assert.equal(calculateXp(100,'basic'),100);
 assert.equal(calculateXp(100,'platina'),330);
 assert.equal(calculateXp(100,'premium'),440);
 assert.equal(calculateXp(1,'platina'),3);
});
test('invalid base and unknown privilege are rejected', () => {
 for(const value of [-1,0,0.5,1001,NaN]) assert.throws(()=>calculateXp(value,null),/invalid_base_points/);
 assert.throws(()=>calculateXp(1,'admin'),/invalid_tier/);
});
