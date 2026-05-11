A vueflow node is an object with the the following form:

```ts
const exampleNode = {
    id: "this_shall_be_unique",
    data: {"whatever": "you want"},
    position: {x: 0, y: 0},
    type: "example-type"
}
```

The type is a string which will be matched with a corresponding node template. The template needs to be declared in the VueFlow component.
See ../components/TopologyDiagram.vue.

As an example:

```vue
<VueFlow v-else :nodes="nodes" :edges="edges">
    <Background></Background>
    <MiniMap pannable zoomable></MiniMap>
    <template #node-example-type="props">
      <ExampleNode v-bind="props" />
    </template>
</VueFlow>
```

The template as the slot #node-example-type which will be matched per name with the type "example-type". When this matches, then the given template is used instead of the default node type. In this example, we would then need to create the ExampleNode.vue component which would be used to render the node.
The "props" passed include the node data, its position, id and several other properties which may be useful to render the node. See https://vueflow.dev/guide/node.html#node-props for full list of props.
