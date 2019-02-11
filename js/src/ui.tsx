import {Data, FieldValues} from "./data";
import * as React from "react";
import {loadPatch, store} from "./app";
import * as ReactDOM from "react-dom";
import {Diff2Html as diff2html} from "diff2html";

const $ = require('jquery');
require('jquery.fancytree');
require("diff2html/dist/diff2html.css");
require('jquery.fancytree/dist/skin-lion/ui.fancytree.css');

const selectedFilters = {};

export function renderWorkspace() {
    // force recreate all workspace, updating fancytree is hard
    const oldWorkspace = document.getElementById("workspace");
    if (oldWorkspace) oldWorkspace.remove();

    const newWorkspace = document.createElement("workspace");
    newWorkspace.id = "workspace";
    document.body.appendChild(newWorkspace);

    const data = store.apply(selectedFilters);

    ReactDOM.render(
        workspace(data),
        newWorkspace
    );

    $("#tree").fancytree({
        source: data.treeRoot._children,
        lazyLoad: function (event, data) {
            data.result = data.node.data._children;
        },
        activate: function (event, data) {
            const leaf = data.node.data.leaf;
            if (leaf) {
                const patch = loadPatch(leaf);
                if (patch) {
                    const x = diff2html;
                    patch.then(text => {
                        if (text.indexOf("[DIFF-ABORTED]") == -1) {
                            // noinspection UnnecessaryLocalVariableJS
                            const diffHtml = x.getPrettyHtml(text, {outputFormat: "side-by-side"});
                            document.getElementById("diff").innerHTML = diffHtml
                        } else {
                            document.getElementById("diff").innerText = text;
                        }
                    })
                } else {
                    document.getElementById("diff").innerText = "Diff not available"
                }
            }
        }
    });
}

export function workspace(data: Data) {
    console.log(data);

    return <div>
        <div>
            {data.fieldValues.map(field => filter(field))}
        </div>
        <div id="tree" style={{position: "absolute", zoom: 0.9}}/>
        <div id="diff" style={{position: "absolute", zoom: 0.8, marginLeft: 650}}/>
    </div>
}

function filter(values: FieldValues) {
    return <label key={values.field} style={{display: "inline-block"}}>
        <div>{values.field}</div>
        <select
            name={values.field}
            size={6}
            defaultValue={values.selected || "all"}>

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