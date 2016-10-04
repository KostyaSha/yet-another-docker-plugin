function expandTextAreaYAD(button, id) {
    button.style.display = "none";
    var field = button.parentNode.previousSibling.children[0];
    var value = field.value.replace(/ +/g, '\n');

    var n = button;
    while (n.tagName != "TABLE") {
        n = n.parentNode;
    }

    n.parentNode.innerHTML =
        "<textarea rows=8 class='setting-input validated' name='" + field.name + "'>" + value + "</textarea>";
    layoutUpdateCallback.call();
}
