document.addEventListener("click", (event) => {
    const opener = event.target.closest("[data-open-modal]");
    if (opener) {
        const dialog = document.getElementById(opener.dataset.modalId);
        if (dialog) {
            dialog.showModal();
        }
        return;
    }

    if (event.target.matches("dialog.student-modal")) {
        event.target.close();
    }
});
