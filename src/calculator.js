/*
# Documentation for `addNumbers` function

## Function

### `addNumbers(a, b)`

This function takes two numbers as arguments and returns their sum.

#### Parameters

- `a` (Number): The first number to be added.
- `b` (Number): The second number to be added.

#### Returns

- (Number): The sum of `a` and `b`.

## Example

```js
const num1 = 50;
const num2 = 10;
console.log(`The sum of ${num1} and ${num2} is:`, addNumbers(num1, num2));
```

In this example, the function `addNumbers` is called with `num1` and `num2` as arguments. The sum of `num1` and `num2` is then logged to the console.

## Module Exports

The `addNumbers` function is exported as a module, which means it can be imported and used in other JavaScript files.

```js
module.exports = addNumbers;
```
*/
function addNumbers(a, b) {
    return a + b;
}

const num1 = 50;
const num2 = 10;
console.log(`The sum of ${num1} and ${num2} is:`, addNumbers(num1, num2));

module.exports = addNumbers;