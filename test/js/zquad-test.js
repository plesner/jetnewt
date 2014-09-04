(function(namespace) {

if (!namespace.zquad)
  return;
var zquad = namespace.zquad;
var test = {};
zquad.test = test;

test.onLoad = function() {
  testEncoding();
};

function assert(value) {
  if (!value)
    FAIL;
}

function testEncoding() {
  var validNames = {};
  for (var i = 0; i < zquad.getBias(8); i++) {
    var quad = zquad.ZQuad.fromQuad(i);
    var name = quad.getName();
    var parsed = zquad.ZQuad.fromName(name);
    assert(i == parsed.getValue());
    validNames[name] = i;
  }
  var v = "aeiouy";
  var c = "bcdfghjklmnpqrstvwxz";
  for (var order = 0; order < 2; order++) {
    for (var ic0 = 0; ic0 < 20; ic0++) {
      for (var iv0 = 0; iv0 < 6; iv0++) {
        for (var ic1 = 0; ic1 < 20; ic1++) {
          for (var iv1 = 0; iv1 < 6; iv1++) {
            var v0 = v.charAt(iv0);
            var c0 = c.charAt(ic0);
            var v1 = v.charAt(iv1);
            var c1 = c.charAt(ic1);
            var word = order ? (v0 + c0 + v1 + c1) : (c0 + v0 + c1 + v1);
            var parsed = zquad.ZQuad.fromName(word)
            if (typeof validNames[word] == "number") {
              assert(parsed.getValue() == validNames[word]);
            } else {
              assert(parsed == null);
            }
          }
        }
      }
    }
  }
}

})(this);
