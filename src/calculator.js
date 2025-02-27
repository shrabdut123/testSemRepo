/*
# Documentation for subtractNumbers function

## Function

### subtractNumbers(a, b)

This function subtracts two numbers and returns the result.

#### Parameters

- `a` (Number): The first number to subtract from.
- `b` (Number): The second number to be subtracted.

#### Returns

- (Number): The result of the subtraction of `b` from `a`.

#### Example

```js
const num1 = 50;
const num2 = 10;
console.log(`The diff of ${num1} and ${num2} is:`, subtractNumbers(num1, num2));
```

This will log: "The diff of 50 and 10 is: 40"

## Module Exports

The `subtractNumbers` function is exported as a module.

```js
module.exports = subtractNumbers;
```

This allows it to be imported and used in other JavaScript files.
*/
function subtractNumbers(a, b) {
    return a - b;
}
const num1 = 50;
const num2 = 10;
console.log(`The diff of ${num1} and ${num2} is:`, subtractNumbers(num1, num2));

module.exports = subtractNumbers;