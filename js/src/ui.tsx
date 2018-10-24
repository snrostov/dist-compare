import {Data, FieldValues} from "./data";
import * as React from "react";
import {loadPatch, store} from "./app";
import * as ReactDOM from "react-dom";
import {Diff2Html as diff2html} from "diff2html";
// import {Diff2HtmlUI} from "diff2html/dist/diff2html-ui";

require("diff2html/dist/diff2html.css");

require('jquery.fancytree/dist/skin-lion/ui.fancytree.css');
const $ = require('jquery');
require('jquery.fancytree');
const selectedFilters = {};

export function renderWorkspace() {
    const e0 = document.getElementById("workspace");
    if (e0) e0.remove();
    const e = document.createElement("workspace");
    e.id = "workspace";
    document.body.appendChild(e);

    console.log(selectedFilters);

    const data = store.apply(selectedFilters);

    ReactDOM.render(
        workspace(data),
        e
    );

    const tree = document.getElementById("tree");

    $("#tree").fancytree({
        // /kotlinc/lib/kotlin-compiler.jar/com/intellij/psi/impl/source/PsiJavaFileBaseImpl.class
        // extensions: ["table"],
        // table: {
        //     indentation: 20,      // indent 20px per node level
        // },
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
                        console.log(text);
                        document.getElementById("diff").innerHTML =
                            x.getPrettyHtml(text, {
                                outputFormat: "side-by-side"
                            });
                    })
                } else {
                    document.getElementById("diff").innerText = "Diff not available"
                }
            }
        }
        // renderColumns: function (event, data) {
        //     let node = data.node,
        //         $tdList = $(node.tr).find(">td");
        //     $tdList.eq(1).text(node.data.count);
        // }
    });
}

export function workspace(data: Data) {
    console.log(data);

    return <div>
        <div>
            {data.fieldValues.map(field => filter(field))}
        </div>
        <div id="tree" style={{position:"absolute",zoom:0.9}}/>
        <div id="diff" style={{position:"absolute",zoom:0.8,marginLeft:650}}/>
    </div>
}

//         <table id="tree">
//             <colgroup>
//                 <col width="*"></col>
//                 <col width="50px"></col>
//             </colgroup>
//             <thead>
//             <tr>
//                 <th></th>
//                 <th></th>
//             </tr>
//             </thead>
//             <tbody>
//             <tr>
//                 <td></td>
//                 <td></td>
//             </tr>
//             </tbody>
//         </table>

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