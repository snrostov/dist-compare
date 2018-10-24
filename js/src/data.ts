interface Item {
    id: String
    relativePath: String
    extension: String
    status: String
    kind: String
    noExtension: boolean
}

export class DataStore {
    all: Item[];
    fields: string[] = ["status", "kind", "extension"];

    load(data: Item[]) {
        this.all = data;

        for (let item of data) {
            if (item.noExtension) {
                item.extension = "NO EXT"
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

                if (itemValue != fieldValues.selected) {
                    matched = false;
                    // don't break, we should collect all values for selected filter
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
                parent.count++;
            }

            parent.leaf = item;
        }

        const fieldValuesList: FieldValues[] = [];
        fieldValues.forEach(value => {
            value.values.sort((a, b) => b.count - a.count);
            return fieldValuesList.push(value);
        });

        rootNode._children[0].collapseSingleChild();

        return new Data(fieldValuesList, rootNode._children[0])
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
    count: number = 0;
    leaf: Item;

    constructor(title: String) {
        this.title = title;
        this.key = title;
    }

    getChild(childName: string) {
        let child = this.childrenByName.get(childName);
        if (!child) {
            child = new Node(childName);
            this.folder = true;
            this.lazy = true;
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
            this._children.push(...child._children);
        } else {
            for (let child of this._children) {
                child.collapseSingleChild()
            }
        }
    }
}