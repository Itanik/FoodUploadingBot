/*
* Добавьте этот код на страницу, где будет размещен раздел food
* <ul id="food"></ul>
* <details>
*   <summary>Архив</summary>
*   <ul id="archive"></ul>
* </details>
* <script src="js/food.js" type="module"></script>
*/
(async function () {
  "use strict";

  function sortByDecreasing(list) {
    return list.sort(function (a, b) {
      if (a.name < b.name) {
        return 1;
      }
      if (a.name > b.name) {
        return -1;
      }
      return 0;
    });
  }

  function renderFoodList(elementId, listOfObjects) {
    sortByDecreasing(listOfObjects);
    let ul = document.getElementById(elementId);
    for (const obj of listOfObjects) {
      let li = document.createElement("li");
      let a = document.createElement("a");
      a.innerHTML = obj.name;
      a.href = obj.path;
      li.append(a);
      ul.append(li);
    }
  }

  function fetchJson(path) {
    return new Promise(async function (resolve, reject) {
      let response = await fetch(path, {
        cache: 'no-cache',
        headers: {'Content-Type': 'application/json'}
      });
      if (response.ok) {
        resolve(await response.json())
      } else {
        reject(new Error(response.statusText))
      }
    });
  }

  function handleJsonRendering(elementId, jsonPath) {
    fetchJson(jsonPath)
      .then(
        listOfObjects => renderFoodList(elementId, listOfObjects),
        alert
      )
      .catch(alert)
  }

  window.document.addEventListener("DOMContentLoaded", function (event) {
    handleJsonRendering('food', '/food/food_files.json');
    handleJsonRendering('archive', '/food/food_archive.json');
  });
})();
