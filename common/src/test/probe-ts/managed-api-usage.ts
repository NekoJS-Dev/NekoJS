const s: string = Stable("hello");

const overloadedStr: string = Overloaded("test");
Overloaded(42);

const cbPayload: (name: string) => void = (name: string) => {};
WithCallback(cbPayload);

const callbackRef: (name: string) => void = (name: string): void => {};
WithCallback(callbackRef);
