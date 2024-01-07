// This is a test harness for your module
// You should do something interesting in this harness
// to test out the module and to provide instructions
// to users on how to use it by example.


// open a single window
const win = Ti.UI.createWindow();
const label = Ti.UI.createLabel();
win.add(label);
win.open();

// TODO: write your module tests here
import ti_documentscanner  from 'ti.documentscanner';
Ti.API.info("module is => " + ti_documentscanner);



if (Ti.Platform.name == "android") {
	it_documentscanner.showScanner();

}
