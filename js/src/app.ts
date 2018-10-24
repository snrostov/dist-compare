// import {Inflate} from "pako/lib/inflate";
import {DataStore} from "./data";
import {renderWorkspace} from "./ui";

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

export function loadPatch(item: Item): Promise<string> {
    // let data = document.getElementById("patch-" + id).innerText;
    // let inflater = new Inflate({});
    // inflater.push(atob(data), true);
    // return new TextDecoder("utf-8").decode(inflater.result)

    return fetch('diff' + item.relativePath + ".patch")
        .then(function (response) {
            return response.text();
        })
}

export const store = new DataStore();

function init(data: Item[]) {
    // store.load(data);
    // renderWorkspace()

    fetch('diff/data.json')
        .then(function (response) {
            return response.json();
        })
        .then(function (myJson) {
            store.load(myJson);
            renderWorkspace()
        });
}

window['init'] = init;