(function(namespace) {

if (!namespace.zquad || namespace.zquad.main)
  return;
var zquad = namespace.zquad;
var main = {};
zquad.main = main;

function getParams() {
  var search = String(location.search);
  if (search.charAt(0) != "?")
    return {};
  var query = search.substring(1);
  var params = {};
  query.split("&").forEach(function (str) {
    var pair = str.split("=");
    params[pair[0]] = pair[1];
  });
  return params;
}

function getQuad(params, callback) {
  if (params.latlng) {
    var pair = params.latlng.split(",");
    var lat = Number(pair[0]);
    var lng = Number(pair[1]);
    callback(zquad.ZQuad.fromWgs84(lat, lng));
  } else if (params.quad) {
    callback(zquad.ZQuad.fromQuad(Number(params.quad)));
  } else if (params.name) {
    callback(zquad.ZQuad.fromName(params.name));
  } else if (params.address) {
    var geocoder = new google.maps.Geocoder();
    geocoder.geocode({'address': params.address}, function(results, status) {
      if (status == google.maps.GeocoderStatus.OK) {
        var location = results[0].geometry.location
        callback(zquad.ZQuad.fromWgs84(location.lat(), location.lng()));
      } else {
        alert("Geocode failed: " + status);
      }
    });
  }
}

function showQuad(quad) {
  var div = document.getElementById("map");
  var map = new google.maps.Map(div, {
    center: quad.getCenter(),
    zoom: 6
  });
  var bounds = quad.getBounds();
  new google.maps.Rectangle({
    strokeColor: '#FF0000',
    strokeOpacity: 0.8,
    strokeWeight: 2,
    fillColor: '#FF0000',
    fillOpacity: 0.35,
    map: map,
    bounds: bounds
  });
  map.fitBounds(bounds);
  google.maps.event.addListener(map, 'click', function(event) {
    console.log(event);
  });
}

function gebi(name) {
  return document.getElementById(name);
}

function floatToString(value) {
  return value.toPrecision(13).replace(/([^0])0+$/,"$1");
}

main.onLoad = function() {
  var params = getParams();
  getQuad(params, function(quad) {
    if (quad == null) {
      alert("Invalid quad");
      return;
    }
    if (params.zoom)
      quad = quad.toZoom(Number(params.zoom));
    var name = quad.getName();
    document.title = "Z-quad: " + name;
    gebi("quad").textContent = quad.getValue();
    gebi("name").textContent = name;
    gebi("zoom").textContent = quad.getZoom();
    gebi("lat").textContent = floatToString(quad.getCenterLat());
    gebi("lng").textContent = floatToString(quad.getCenterLng());
    var parent = gebi("parent");
    if (quad.getZoom() != 0) {
      parent.style.visibility = "visible";
      parent.href = "?name=" + quad.getAncestor(1).getName();
    }
    if (quad.getZoom() < zquad.kMaxLevel) {
      for (var i = 0; i < 4; i++) {
        var elm = gebi("c" + i);
        elm.style.visibility = "visible";
        var child = zquad.ZQuad.fromQuad(i + 1, 1);
        elm.href = "?name=" + quad.getDescendant(child).getName();
      }
    }
    showQuad(quad);
  });
}

})(this);
