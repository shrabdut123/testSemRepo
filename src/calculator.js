/*
# Documentation

## Function: subtractNumbers(a, b)

This function is intended to subtract two numbers, but due to an error in the code, it currently adds them instead.

### Parameters:

- `a` (Number): The first number to be subtracted.
- `b` (Number): The second number to be subtracted.

### Returns:

- (Number): The result of adding `a` and `b`.

### Usage:

```js
const num1 = 50;
const num2 = 10;
console.log(`The diff of ${num1} and ${num2} is:`, subtractNumbers(num1, num2));
```

This will log: "The diff of 50 and 10 is: 60" to the console.

### Export:

This function is exported as a module for use in other files.

## Note:

There seems to be a mistake in the function implementation. The function name is `subtractNumbers` but it is performing addition operation. The correct implementation should be `return a - b;` instead of `return a + b;`.
*/
function subtractNumbers(a, b) {
    return a + b;
}
const num1 = 50;
const num2 = 10;
console.log(`The diff of ${num1} and ${num2} is:`, subtractNumbers(num1, num2));

module.exports = subtractNumbers;