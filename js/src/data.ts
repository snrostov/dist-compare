interface Item {
    id: string
    relativePath: string
    extension: string
    status: string
    kind: string
    noExtension: boolean
    diffs: number,
    deltas: Array<number>
}

function isDiffStatus(status: String) {
    return status != 'MATCHED' && status != 'COPY' && status != null
}

export class DataStore {
    all: Item[];
    fields: string[] = ["status", "kind", "extension"];

    load(data: Item[]) {
        this.all = [];

        for (let item of data) {
            if (item) {
                if (item.noExtension || item.extension.length == 0) item.extension = "NO EXT";
                this.all.push(item)
            }
        }
    }

    apply(selectedFieldValues: {}): Data {
        const fieldValues = new Map<string, FieldValues>();

        const filteredFields: FieldValues[] = [];
        const unfilteredFields: string[] = [];

        for (let field of this.fields) {
            const selected = selectedFieldValues[field];
            const filter = new FieldValues(field, selected);
            fieldValues.set(field, filter);

            if (selected) filteredFields.push(filter);
            else unfilteredFields.push(field)
        }

        const filteredItems: Item[] = [];

        for (let item of this.all) {
            let matched = true;

            for (let fieldValues of filteredFields) {
                const itemValue = item[fieldValues.field];

                let selectedValue = fieldValues.selected;
                if (selectedValue == 'diff') {
                    if (!isDiffStatus(itemValue)) {
                        matched = false;
                        // don't break, we should collect all values for selected filter
                    }
                } else {
                    if (itemValue != selectedValue) {
                        matched = false;
                        // don't break, we should collect all values for selected filter
                    }
                }

                fieldValues.getValue(itemValue).count++;
            }

            if (matched) filteredItems.push(item)
        }

        const rootNode = new Node("root");
        for (let item of filteredItems) {
            for (let field of unfilteredFields) {
                fieldValues.get(field).getValue(item[field]).count++;
            }

            const path = item.relativePath.split("/");
            let parent = rootNode;
            for (let childName of path) {
                parent = parent.getChild(childName);
                parent.counts[item.status]++;
                parent.diffs += item.diffs;
            }

            parent.leaf = item;
            parent.item = item;
        }

        const fieldValuesList: FieldValues[] = [];
        fieldValues.forEach(value => {
            if (value.field == 'status') {
                let diff = 0;
                value.values.forEach(value => {
                    if (isDiffStatus(value.value)) {
                        diff += value.count;
                    }
                });
                value.values.push(new FilterValue('diff', diff))
            }

            value.values.sort((a, b) => b.count - a.count);
            return fieldValuesList.push(value);
        });

        rootNode.addStatusClass(false);
        rootNode.collapseSingleChild();
        rootNode.addCounts();

        return new Data(fieldValuesList, rootNode)
    }
}

export class Data {
    constructor(public fieldValues: FieldValues[], public treeRoot: Node) {
    }
}

export class FieldValues {
    byValue = new Map<string, FilterValue>();
    values: FilterValue[] = [];

    constructor(public field: string, public selected: string | null) {
    }

    getValue(value: string): FilterValue {
        let filterValue: FilterValue = this.byValue.get(value);
        if (!filterValue) {
            filterValue = new FilterValue(value, 0);
            this.byValue.set(value, filterValue);
            this.values.push(filterValue)
        }
        return filterValue
    }
}

export class FilterValue {
    constructor(public value: string, public count: number) {
    }
}

export class Node /*implements FancytreeNode*/ {
    title: String;
    key: String;
    folder: Boolean = false;
    lazy = false;

    childrenByName: Map<string, Node> = new Map();
    _children: Node[] = [];
    diffs: number = 0;
    counts: { [key: string]: number } = {MATCHED: 0, MISSED: 0, UNEXPECTED: 0, MISMATCHED: 0, COPY: 0};
    leaf: Item;
    item: Item;
    unpaired: boolean = false;

    constructor(title: String) {
        this.title = title;
        this.key = title;
    }

    getChild(childName: string) {
        let child = this.childrenByName.get(childName);
        if (!child) {
            child = new Node(childName);
            this._children.push(child);
            this.childrenByName.set(childName, child);
        }
        return child;
    }

    collapseSingleChild() {
        if (this._children.length == 1) {
            const child = this._children[0];
            child.collapseSingleChild();

            this.title = this.title + "/" + child.title;
            this._children = child._children;
            this.leaf = child.leaf
        } else {
            for (let child of this._children) {
                child.collapseSingleChild()
            }
        }
    }

    addCounts() {
        if (this._children.length > 0) {
            let ok = 0;
            let countHtml = '';
            for (let status in this.counts) {
                if (isDiffStatus(status)) {
                    const statusCount = this.counts[status];
                    if (statusCount != 0) {
                        if (countHtml.length != 0) countHtml += ' / ';
                        countHtml += "<span class='" + status.toLowerCase() + "'>" + this.counts[status] + "</span>"
                    }
                } else {
                    ok += this.counts[status];
                }
            }

            if (ok > 0) {
                countHtml += " / " + ok;
            }

            this.title = this.title + " (" + countHtml + ")";

            this.folder = true;
            this.lazy = true;
        }

        for (let child of this._children) {
            child.addCounts()
        }
    }

    addStatusClass(parentUnpaired: boolean) {
        if (parentUnpaired) {
            this.title = "<span class='unpaired'>" + this.title + "</span>"
        } else {
            if (this.item != null) {
                if (isDiffStatus(this.item.status)) {
                    this.title = "<span class='" + this.item.status.toLowerCase() + "'>" + this.title + "</span>"
                }
            }
        }

        this.unpaired = parentUnpaired
            || (this.item != null && (this.item.status == "MISSED" || this.item.status == "UNEXPECTED"));

        for (let child of this._children) {
            child.addStatusClass(this.unpaired)
        }
    }
}