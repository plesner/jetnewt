(function (namespace) {

if (namespace.zquad)
  return;
var zquad = {};
namespace.zquad = zquad;

// The highest zoom level that will fit in a 53 bit integer.
zquad.kMaxLevel = 26;

// A wrapper around a z-quad.
zquad.ZQuad = ZQuad;
function ZQuad(quad, zoom) {
  this.quad = quad;
  this.zoom = zoom;
}

// Returns this quad's zoom level.
ZQuad.prototype.getZoom = function() {
  return this.zoom;
};

ZQuad.prototype.toZoom = function(z) {
  var zoom = this.getZoom();
  if (z >= zoom) {
    return this;
  } else {
    return this.getAncestor(zoom - z);
  }
};

ZQuad.prototype.getAncestor = function(n) {
  var newQuad = Math.floor((this.quad - getBias(n)) / Math.pow(4, n));
  var newZoom = this.getZoom() - n;
  return new ZQuad(newQuad, newZoom);
};

ZQuad.prototype.getDescendancy = function(n) {
  var bias = getBias(n);
  var newQuad = (this.quad - bias) % Math.pow(4, n) + bias;
  return new ZQuad(newQuad, n);
};

ZQuad.prototype.getDescendant = function(v) {
  var subZoom = v.getZoom();
  var newQuad = (this.getValue() * Math.pow(4, subZoom)) + v.getValue();
  return new ZQuad(newQuad, this.getZoom() + subZoom);
};

// Returns the integer quad value.
ZQuad.prototype.getValue = function() {
  return this.quad;
};

ZQuad.prototype._getScalar = function() {
  return this.quad - getBias(this.getZoom());
};

ZQuad.prototype.getCenterUnitLat = function() {
  var x = compactWord52(Math.floor(this._getScalar() / 2));
  var zoom = this.getZoom();
  if (zoom < zquad.kMaxLevel) {
    x = (2 * x) + 1;
    zoom += 1;
  }
  return x / Math.pow(2, zoom);
};

ZQuad.prototype.getCenterUnitLng = function() {
  var y = compactWord52(this._getScalar());
  var zoom = this.getZoom();
  if (zoom < zquad.kMaxLevel) {
    y = (2 * y) + 1;
    zoom += 1;
  }
  return y / Math.pow(2, zoom);
}

ZQuad.prototype.getCornerUnitLat = function() {
  return compactWord52(Math.floor(this._getScalar() / 2)) / Math.pow(2, this.getZoom());
};

ZQuad.prototype.getCornerUnitLng = function() {
  return compactWord52(this._getScalar()) / Math.pow(2, this.getZoom());
};

ZQuad.prototype.getCenterLat = function() {
  return -this.getCenterUnitLat() * 180 + 90;
};

ZQuad.prototype.getCenterLng = function() {
  return this.getCenterUnitLng() * 360 - 180;
};

ZQuad.prototype.getCornerLat = function() {
  return -this.getCornerUnitLat() * 180 + 90;
};

ZQuad.prototype.getCornerLng = function() {
  return this.getCornerUnitLng() * 360 - 180;
};

ZQuad.prototype.getWidth = function() {
  return 360 / Math.pow(2, this.getZoom());
};

ZQuad.prototype.getHeight = function() {
  return 180 / Math.pow(2, this.getZoom());
};

ZQuad.prototype.getName = function() {
  var parts = [];
  var current = this;
  do {
    var nextChunk, nextChunkZoom;
    if (current.getZoom() % 7 == 0) {
      nextChunkZoom = Math.min(current.getZoom(), 7);
    } else {
      nextChunkZoom = current.getZoom() % 7;
    }
    var nextChunk = current.getDescendancy(nextChunkZoom);
    current = current.getAncestor(nextChunkZoom);
    parts.push(nextChunk._getChunkName());
  } while (current.getValue() != 0);
  return parts.reverse().join("-");
};

function getLevel7CenterScalar(quad, zoom) {
  var scalar = quad - getBias(zoom);
  if (zoom == 7)
    return scalar;
  var middleChild = 4 * scalar + 3;
  var zoomDelta = 7 - zoom;
  return middleChild * Math.pow(4, zoomDelta - 1);
}

var kRegion5s = [
  [0, 0, 1, 1],
  [0, 0, 1, 1],
  [0, 2, 2, 1],
  [3, 2, 2, 4],
  [3, 3, 4, 4],
  [3, 3, 4, 4]
];

var kGroup6Shapes = [
  [0, 1, 0, 1],
  [2, 3, 2, 3],
  [4, 0, 1, 5],
  [6, 2, 3, 7],
  [0, 1, 0, 1],
  [2, 3, 2, 3]
];

var kGroup6Cell20s = [
  [
    [0,  -1], [0,   2], [0,   3], [0,   4],
    [0,   5], [0,  -7], [0,   8], [0, -10],
    [0,  11], [0,  12], [0, -14], [2,   0],
    [0,  15], [0, -17], [2,   2], [2,  -4],
  ], [
    [1,  -1], [1,   2], [1,   3], [1,   4],
    [1,   5], [1,  -7], [1,   8], [1, -10],
    [2,   1], [1,  11], [1, -13], [1,  14],
    [2,   5], [2,  -7], [1,  15], [1, -17],
  ], [
    [3,  -1], [3,   2], [2,   8], [2,   9],
    [3,   3], [3,  -5], [3,   6], [2, -14],
    [3,   7], [3,   8], [3, -10], [3,  11],
    [3,  12], [3, -14], [3,  15], [3, -17]
  ], [
    [2, -11], [2,  12], [4,   0], [4,   1],
    [2,  15], [4,  -3], [4,   4], [4,  -6],
    [4,   7], [4,   8], [4, -10], [4,  11],
    [4,  12], [4, -14], [4,  15], [4, -17]
  ], [
    [3, -19], [5,   0], [5,   1], [5,   2],
    [5,   3], [5,  -5], [5,   6], [5,  -8],
    [5,   9], [5,  10], [5, -12], [5,  13],
    [5,  14], [5, -16], [5,  17], [5, -19],
  ], [
    [4, -19], [5,   0], [5,   1], [5,   2],
    [5,   3], [5,  -5], [5,   6], [5,  -8],
    [5,   9], [5,  10], [5, -12], [5,  13],
    [5,  14], [5, -16], [5,  17], [5, -19],
  ], [
    [5,  -1], [5,   2], [5,   3], [5,   4],
    [5,   5], [5,  -7], [5,   8], [5, -10],
    [5,  11], [5,  12], [5, -14], [5,  15],
    [5,  16], [5, -18], [5,  19], [0, -19],
  ], [
    [5,  -1], [5,   2], [5,   3], [5,   4],
    [5,   5], [5,  -7], [5,   8], [5, -10],
    [5,  11], [5,  12], [5, -14], [5,  15],
    [5,  16], [5, -18], [5,  19], [1, -19],
  ]
];

function getGroup6AndCell20(x, y) {
  var group6Shape = kGroup6Shapes[Math.floor(y / 4)][Math.floor(x / 4)];
  return kGroup6Cell20s[group6Shape][(x % 4) + 4 * (y % 4)];
}

var kGroup6CellsInv = (function() {
  var result = Array(5 * 6 * 20);
  for (var x = 0; x < 16; x++) {
    for (var y = 0; y < 24; y++) {
      var index = (16 * y) + x;
      var region5 = kRegion5s[Math.floor(y / 4)][Math.floor(x / 4)];
      var group6AndCell20 = getGroup6AndCell20(x, y);
      var group6 = group6AndCell20[0];
      var cell20 = group6AndCell20[1];
      var offset = (6 * 20 * region5) + (20 * group6);
      if (cell20 < 0) {
        result[offset - cell20] = [index, false];
        result[offset - cell20 - 1] = [index, true];
      } else {
        result[offset + cell20] = [index, false];
      }
    }
  }
  return result;
})();


var kVowels = "aeiouy";
var kConsonants = "bcdfghjklmnpqrstvwxz";

ZQuad.prototype._getChunkName = function() {
  var redirection = kRedirectedChunkToName[this.getValue()];
  if (typeof redirection == "string")
    return redirection;
  var zoomDelta = 7 - this.getZoom();
  var isZoomed = (zoomDelta > 0);
  var scalar = getLevel7CenterScalar(this.getValue(), this.getZoom());
  var x = compactWord32(scalar);
  var y = compactWord32(Math.floor(scalar / 2));
  // Split into two divisions.
  var isEastern = (x >= 64);
  if (isEastern)
    x -= 64;
  // Split each division into six sections.
  var section6 = 2 * Math.floor(y / 44) + Math.floor(x / 32);
  x = x % 32;
  y = y % 44;
  // Split each section into four blocks.
  var block4 = Math.floor(x / 16) + Math.floor(y / 24) * 2;
  x = x % 16;
  y = y % 24;
  var region5 = kRegion5s[Math.floor(y / 4)][Math.floor(x / 4)];
  var group6AndCell20 = getGroup6AndCell20(x, y);
  var group6 = group6AndCell20[0];
  var cell20 = group6AndCell20[1];
  if (cell20 < 0)
    cell20 = (isZoomed ? -1 : 0) - cell20;
  var v0 = kVowels.charAt(section6);
  var c0 = kConsonants.charAt(5 * block4 + region5);
  var v1 = kVowels.charAt(group6);
  var c1 = kConsonants.charAt(cell20);
  return isEastern
    ? c0 + v0 + c1 + v1
    : v0 + c0 + v1 + c1;
};

// An array where the i'th index is the i'th zoom level bias.
var kBiases = (function() {
  var result = [];
  var current = 0;
  for (var i = 0; i <= zquad.kMaxLevel; i++) {
    result.push(current);
    current = (4 * current) + 1;
  }
  return result;
})();

// Returns the n'th bias. Works up to the max zoom level.
zquad.getBias = getBias;
function getBias(n) {
  return kBiases[n];
}

// Given a WGS84 lat/lng returns the max zoom level quad that contains that
// point.
ZQuad.fromWgs84 = function(geoLat, geoLng) {
  var unitLat = (90 - geoLat) / 180;
  var unitLng = (180 + geoLng) / 360;
  return ZQuad.fromUnit(unitLat, unitLng);
};

// Spread the bits in a 16-bit short out over full 32 bits. Javascript's bit
// operations only work on 32-bit words, that's why this function is limited
// to 16-bit inputs.
function spreadWord16(n) {
  // n = 0000000000000000abcdefghijklmnop
  var r = ((n << 8) & 0x00FF0000) | (n & 0x000000FF);
  // r = 00000000abcdefgh00000000ijklmnop
  r = ((r << 4) & 0x0F000F00) | (r & 0x000F000F);
  // r = 0000abcd0000efgh0000ijkl0000mnop
  r = ((r << 2) & 0x30303030) | (r & 0x03030303);
  // r = 00ab00cd00ef00gh00ij00kl00mn00op
  r = ((r << 1) & 0x44444444) | (r & 0x11111111);
  // r = 0a0b0c0d0e0f0g0h0i0j0k0l0m0n0o0p
  return r;
}

// Spread the bits in a 26-bit words out over the full 52 bits. Javascript
// doesn't support more than 53 significant bits accurately so don't use this
// for values that yield results larger than 2^52.
function spreadWord26(n) {
  var highBits = spreadWord16((n >>> 16) & 0xFFFF);
  var lowBits = spreadWord16(n & 0xFFFF);
  return (highBits * Math.pow(2, 32)) + lowBits;
}

// Compact 16 bits spread alternating over 32 bits down to the lower 16 bits in
// a word.
function compactWord32(n) {
  // n = 0a0b0c0d0e0f0g0h0i0j0k0l0m0n0o0p
  var r = ((n >>> 1) & 0x22222222) | (n & 0x11111111);
  // r = 00ab00cd00ef00gh00ij00kl00mn00op
  r = ((r >>> 2) & 0x0C0C0C0C) | (r & 0x03030303);
  // r = 0000abcd0000efgh0000ijkl0000mnop
  r = ((r >>> 4) & 0x00F000F0) | (r & 0x000F000F);
  // r = 00000000abcdefgh00000000ijklmnop
  r = ((r >>> 8) & 0x0000FF00) | (r & 0x000000FF);
  // r = 0000000000000000abcdefghijklmnop
  return r;
}

// Compact 26 bits spread alternating over 52 bits down to the lower 26 bits in
// a 32-bit word.
function compactWord52(n) {
  var highBits = compactWord32((n / Math.pow(2, 32)) & 0xFFFFFFFF);
  var lowBits = compactWord32(n & 0xFFFFFFFF);
  return (highBits * Math.pow(2, 16)) + lowBits;
}

// Given two unit coordinates, returns the quad that contains them.
ZQuad.fromUnit = function(unitLat, unitLng) {
  var x = Math.floor(unitLat * Math.pow(2, zquad.kMaxLevel));
  var y = Math.floor(unitLng * Math.pow(2, zquad.kMaxLevel));
  var sx = spreadWord26(x);
  var sy = spreadWord26(y);
  var scalar = (sx * 2) + sy;
  var bias = getBias(zquad.kMaxLevel);
  var result = new ZQuad(scalar + bias, zquad.kMaxLevel);
  return result;
};

ZQuad.fromName = function(string) {
  var parts = string.split("-").map(ZQuad._parseChunk);
  var result = ZQuad.fromQuad(0);
  for (var i = 0; i < parts.length; i++) {
    var part = parts[i];
    if (part == null)
      return null;
    result = result.getDescendant(part);
  }
  return result;
};

function isConsonant(chr) {
  return kConsonants.indexOf(chr) != -1;
}

function isVowel(chr) {
  return kVowels.indexOf(chr) != -1;
}

var kRedirections = [
  [6107, "anax"],
  [6160, "anux"],
  [11758, "raze"]
];

var kRedirectedChunkToName = (function() {
  var result = {};
  for (var i = 0; i < kRedirections.length; i++) {
    var pair = kRedirections[i];
    result[pair[0]] = pair[1];
  }
  return result;
})();

var kRedirectedNameToChunk = (function() {
  var result = {};
  for (var i = 0; i < kRedirections.length; i++) {
    var pair = kRedirections[i];
    result[pair[1]] = pair[0];
  }
  return result;
})();

ZQuad._parseChunk = function(chunk) {
  if (chunk.length != 4)
    return null;
  var redirection = kRedirectedNameToChunk[chunk];
  if (typeof redirection == "number")
    return ZQuad.fromQuad(redirection, getZoom(redirection));
  var isEastern = isConsonant(chunk.charAt(0));
  var cOffset = isEastern ? 0 : 1;
  var vOffset = 1 - cOffset;
  var c0 = chunk.charAt(cOffset);
  var v0 = chunk.charAt(vOffset);
  var c1 = chunk.charAt(cOffset + 2);
  var v1 = chunk.charAt(vOffset + 2);
  if (!(isConsonant(c0) && isVowel(v0) && isConsonant(c1) && isVowel(v1)))
    return null;
  var ic0 = kConsonants.indexOf(c0);
  var block4 = Math.floor(ic0 / 5);
  var region5 = ic0 % 5;
  var sector6 = kVowels.indexOf(v0);
  var cell20 = kConsonants.indexOf(c1);
  var group6 = kVowels.indexOf(v1);
  var cellIndexAndZoomed = kGroup6CellsInv[(20 * 6 * region5) + (20 * group6) + cell20];
  if (cellIndexAndZoomed == null)
    return null;
  var cellIndex = cellIndexAndZoomed[0];
  var isZoomed = cellIndexAndZoomed[1];
  var x = cellIndex % 16;
  var y = Math.floor(cellIndex / 16);
  x += 16 * (block4 % 2);
  y += 24 * Math.floor(block4 / 2);
  var third = Math.floor(sector6 / 2);
  if (y >= 44 || (third == 2 && y >= 40))
    return null;
  x += 32 * (sector6 % 2);
  y += 44 * Math.floor(sector6 / 2);
  x += isEastern ? 64 : 0;
  var scalar = spreadWord16(x) + (spreadWord16(y) * 2);
  var zoomDelta = 0;
  if (isZoomed) {
    zoomDelta = 7;
    for (var i = 0; i <= 7; i++) {
      if (((scalar >> (i << 1)) & 3) == 3) {
        zoomDelta = i + 1;
        break;
      }
    }
  }
  var level7Result = new ZQuad(scalar + getBias(7), 7);
  var result = level7Result.toZoom(7 - zoomDelta);
  if (typeof kRedirectedChunkToName[result.getValue()] == "string")
    return null;
  var level7CenterScalar = getLevel7CenterScalar(result.getValue(), result.getZoom());
  return (level7CenterScalar == scalar) ? result : null;
};

function getZoom(quad) {
  if (quad == 0)
    return 0;
  for (var i = 0; i < zquad.kMaxLevel; i++) {
    if (quad < getBias(i + 1))
      return i;
  }
}

ZQuad.fromQuad = function(quad) {
  return new ZQuad(quad, getZoom(quad));
};

})(this);
