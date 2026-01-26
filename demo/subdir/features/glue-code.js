const {Given, When} = require("@cucumber/cucumber");

Given('one', function () {
    console.log("ONE")
});

When('two', function () {
    console.log("TWO")
})

When('fail', function () {
    throw new Error("FAIL")
})

When('value is {string}', function (foo) {
    console.log(foo)
})