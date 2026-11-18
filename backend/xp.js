'use strict';
// Multiplicadores confirmados pelo proprietário; resultado sempre inteiro e sem ponto flutuante.
const MULTIPLIERS = Object.freeze({ basic: 10, platina: 33, premium: 44 });
function calculateXp(basePoints, verifiedTier) {
  if (!Number.isSafeInteger(basePoints) || basePoints < 1 || basePoints > 1000) throw new Error('invalid_base_points');
  if (verifiedTier !== null && !Object.hasOwn(MULTIPLIERS, verifiedTier)) throw new Error('invalid_tier');
  const tenths = verifiedTier === null ? 10 : MULTIPLIERS[verifiedTier];
  return Math.floor(basePoints * tenths / 10);
}
module.exports = { calculateXp };
