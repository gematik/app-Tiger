A vueflow node is an object with the the following form:

```ts
const exampleNode = {
  id: "this_shall_be_unique",
  data: { whatever: "you want" },
  position: { x: 0, y: 0 },
  type: "example-type",
};
```

The type is a string which will be matched with a corresponding node type. The node type needs to be added to the nodeTypes list which is used as configuration source to the VueFlow component.

As an example:

```ts
const nodeTypes: Record<string, any> = {
  default: markRaw(DefaultNode),
  "example-type": markRaw(ExampleNode),
};
```

Based on the type property of the node, the corresponding node type is used. All props are passed to the corresponding node type component. It is important to use the markRaw function. See https://vueflow.dev/guide/node.html#node-types-object for more information.
