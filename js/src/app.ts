import {Inflate} from "pako/lib/inflate";
import {DataStore} from "./data";
import {renderWorkspace, workspace} from "./ui";
import * as ReactDOM from "react-dom";

require("reset-css/reset.css");

// {
//   "id": 52837,
//   "relativePath": "/lib/kotlin-script-util.jar/org/jetbrains/kotlin/script/util/KotlinAnnotatedScriptDependenciesResolver.class",
//   "noExtension": false,
//   "extension": "class",
//   "status": "MISMATCHED",
//   "kind": "CLASS",
//   "suppressed": false
// }
interface Item {
    id: String
    relativePath: String
    extension: String
    status: String
    kind: String
    noExtension: boolean
}

function loadPatch(id) {
    let data = document.getElementById("patch-" + id).innerText;
    let inflater = new Inflate({});
    inflater.push(atob(data));
    return inflater.result
}

export const store = new DataStore();

function init(data: Item[]) {
    fetch('data.json')
        .then(function (response) {
            return response.json();
        })
        .then(function (myJson) {
            store.load(myJson);
            renderWorkspace()
        });
}

window['init'] = init;