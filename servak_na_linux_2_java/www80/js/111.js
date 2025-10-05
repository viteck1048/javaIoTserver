// ==UserScript==
// @name         Dowmload VSIX
// @namespace    http://tampermonkey.net/
// @version      1.0.0
// @description  This userscript allows you to download VSIX files directly from the Visual Studio Marketplace
// @updateURL    https://gist.githubusercontent.com/Sigmanor/8e367568ba3dd88b8a2c30fb7ec083e3/raw/script.user.js
// @downloadURL  https://gist.githubusercontent.com/Sigmanor/8e367568ba3dd88b8a2c30fb7ec083e3/raw/script.user.js
// @author       sigmanor
// @homepageURL  https://github.com/Sigmanor
// @match        https://marketplace.visualstudio.com/*
// @icon         https://www.google.com/s2/favicons?sz=64&domain=visualstudio.com
// @grant        none
// ==/UserScript==

//https://gist.github.com/Sigmanor/8e367568ba3dd88b8a2c30fb7ec083e3

(function() {
    "use strict";

    function getVersion() {
        const versionElement = document.querySelector(".ux-table-metadata > tbody:nth-child(1) > tr:nth-child(1) > td:nth-child(2)");
        return versionElement ? versionElement.textContent.trim() : null;
    }

    function getPublisherAndExtension() {
        const urlParams = new URLSearchParams(window.location.search);
        const itemName = urlParams.get("itemName");
        if (!itemName) return { publisher: null, extension: null };

        const [publisher, extension] = itemName.split(".");
        return { publisher, extension };
    }

    function addLink() {
        const version = getVersion();
        const { publisher, extension } = getPublisherAndExtension();

        if (!version || !publisher || !extension) return;

        const container = document.querySelector(".ux-section-resources ul");
        if (container && !container.querySelector(".custom-vsix-link")) {
            const listItem = document.createElement("li");
            const link = document.createElement("a");

            link.href = `https://marketplace.visualstudio.com/_apis/public/gallery/publishers/${publisher}/vsextensions/${extension}/${version}/vspackage`;
            link.target = "_blank";
            link.rel = "noreferrer noopener nofollow";
            link.textContent = "Download VSIX";
            link.classList.add("custom-vsix-link");

            link.style.display = "inline-block";
            link.style.backgroundColor = "#ffcc00";
            link.style.color = "#000";
            link.style.fontWeight = "bold";
            link.style.padding = "6px 12px";
            link.style.borderRadius = "5px";
            link.style.textDecoration = "none";
            link.style.marginTop = "5px";
            link.style.boxShadow = "2px 2px 5px rgba(0, 0, 0, 0.2)";
            link.style.transition = "background-color 0.3s, box-shadow 0.3s";

            link.addEventListener("mouseover", () => {
                link.style.backgroundColor = "#ff9900";
                link.style.boxShadow = "3px 3px 7px rgba(0, 0, 0, 0.3)";
            });

            link.addEventListener("mouseout", () => {
                link.style.backgroundColor = "#ffcc00";
                link.style.boxShadow = "2px 2px 5px rgba(0, 0, 0, 0.2)";
            });

            listItem.appendChild(link);
            container.appendChild(listItem);
        }
    }

    const observer = new MutationObserver(() => addLink());
    observer.observe(document.body, { childList: true, subtree: true });

    addLink();
})();