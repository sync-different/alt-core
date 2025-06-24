document.addEventListener("DOMContentLoaded", function () {
    window.onload = function () {
        const overlay = document.getElementById("loading-overlay");
        overlay.style.display = "none";
    };

    setTimeout(function () {
        const overlay = document.getElementById("loading-overlay");
        overlay.style.display = "none";
    }, 5000);
});