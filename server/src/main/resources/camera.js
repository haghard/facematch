function startCamera(where) {
  Webcam.set({
    width: 320,
    height: 240,
    image_format: 'jpeg',
    jpeg_quality: 100
  });
  Webcam.attach(where);
}

function takeSnapshot(photo, im) {
  // take snapshot and get image data
  Webcam.snap(function(data_uri) {
    // display results in page
    //'<h2>Here is your image:</h2>' +
    document.getElementById(photo).innerHTML =
        '<img id= "'+ im +'" src="'+ data_uri +'"/>';
  } );
}