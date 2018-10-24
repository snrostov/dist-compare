import {Data, FieldValues} from "./data";
import * as React from "react";
import {store} from "./app";
import * as ReactDOM from "react-dom";

require('jquery.fancytree/dist/skin-lion/ui.fancytree.css');
const $ = require('jquery');
const fancytree = require('jquery.fancytree');

const selectedFilters = {};

export function renderWorkspace() {
    console.log(selectedFilters);

    const data = store.apply(selectedFilters);

    ReactDOM.render(
        workspace(data),
        document.getElementById("workspace")
    );

    const tree = document.getElementById("tree");

    $("#tree").fancytree({
        source: data.treeRoot._children,
        lazyLoad: function(event, data) {
            data.result = data.node.data._children;
        }
    });
}

export function workspace(data: Data) {
    console.log(data);

    return <div>
        <div>
            {data.fieldValues.map(field => filter(field))}
        </div>
        <div id="tree">

        </div>
    </div>;
}

function filter(values: FieldValues) {
    return <label key={values.field} style={{display: "inline-block"}}>
        <div>{values.field}</div>
        <select
            name={values.field}
            size={5}
            defaultValue={values.selected || "all"}
        >
            <option
                key={"all"}
                value={"all"}
                onClick={() => selectFilter(values.field, null)}>
                all
            </option>

            {values.values.map(value =>
                <option
                    key={value.value}
                    value={value.value}
                    onClick={() => selectFilter(values.field, value.value)}>
                    {value.value}
                    {" "}
                    {values.selected ? "[" + value.count + "]" : "(" + value.count + ")"}
                </option>
            )}
        </select>
    </label>
}

function selectFilter(field: string, value: string | null) {
    selectedFilters[field] = value;
    renderWorkspace()
}