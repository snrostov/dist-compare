import {Inflate} from "pako/lib/inflate";

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

function init(data: Item[]) {
    let all = data;

    const div = document.createElement("div");
    document.body.appendChild(div);

    const activeFilters = {};
    const byFieldAndValue: Map<any, Map<any, Item[]>> = new Map();
    const fields = ["status", "kind", "extension"];
    for (let item of data) {
        if (item.noExtension) {
            item.extension = "-"
        }

        for (let field of fields) {
            let byValue = byFieldAndValue.get(field);
            if (!byValue) {
                byFieldAndValue.set(field, byValue = new Map<any, Item>())
            }

            const value = item[field];
            let list = byValue.get(value);
            if (!list) {
                byValue.set(value, list = [])
            }

            list.push(item)
        }
    }

    console.log(byFieldAndValue);

    function addCheckbox(field, value, title, checkboxes, container) {
        const checkBoxDiv = document.createElement("div");
        const checkbox = document.createElement("input");
        checkbox.checked = activeFilters[field] == value;
        checkbox.addEventListener("click", function () {
            activeFilters[field] = value;
            for (let checkbox1 of checkboxes) {
                if (checkbox1 != checkbox) checkbox1.checked = false
            }
            filter()
        });
        checkbox.type = "checkbox";
        checkboxes.push(checkbox);
        checkBoxDiv.appendChild(checkbox);
        checkBoxDiv.appendChild(document.createTextNode(title));
        checkBoxDiv.appendChild(document.createTextNode(" "));

        const values = byFieldAndValue.get(field).get(value);
        const count = values ? values.length : "-";

        const countText = document.createElement("span");
        countText.innerText = count;
        countText.style.color = "gray";

        checkBoxDiv.appendChild(countText);
        container.appendChild(checkBoxDiv);

        return checkbox
    }

    for (let field of fields) {
        const fieldValues = document.createElement("div");
        fieldValues.style.border = "1px solid";
        div.appendChild(fieldValues);

        const byValue = byFieldAndValue.get(field);
        const values = byValue.keys();
        const checkboxes = [];

        addCheckbox(field, undefined, "(ALL)", checkboxes, fieldValues);

        while (true) {
            let next = values.next();
            if (next.done) break;
            const value = next.value;

            addCheckbox(field, value, value, checkboxes, fieldValues);
        }
    }

    function filter() {
        const filtered = [];
        for (let item of all) {
            let matched = true;
            for (let field in activeFilters) {
                const value = activeFilters[field];
                if (value) {
                    if (item[field] != value) {
                        matched = false;
                        break;
                    }
                }
            }
            if (matched) filtered.push(item)
        }

        console.log(filtered)
    }
}

