// Скрипт для передачі свайпів з iframe до батьківського вікна
(function() {
    // Перевіряємо, що ми в iframe
    if (window.self === window.top) return;
    
    let touchStartX = 0;
    let touchEndX = 0;
    let touchStartY = 0;
    let touchEndY = 0;
    let touchStartTime = 0;
    
    document.addEventListener('touchstart', function(event) {
        touchStartX = event.changedTouches[0].screenX;
        touchStartY = event.changedTouches[0].screenY;
        touchStartTime = Date.now();
    }, false);
    
    document.addEventListener('touchend', function(event) {
        touchEndX = event.changedTouches[0].screenX;
        touchEndY = event.changedTouches[0].screenY;
        handleSwipeGesture();
    }, false);
    
    function handleSwipeGesture() {
        const deltaX = Math.abs(touchStartX - touchEndX);
        const deltaY = Math.abs(touchStartY - touchEndY);
        const deltaTime = Date.now() - touchStartTime;
        
        // Перевіряємо, що це горизонтальний свайп достатньої довжини
        // І що дія тривала менше 0.3 секунди (300 мс)
        if (deltaX > 50 && deltaX > deltaY && deltaTime < 300) {
            let direction = '';
            
            if (touchEndX < touchStartX) {
                direction = 'left'; // свайп вліво
            } else if (touchEndX > touchStartX && touchStartX < 200) {
                direction = 'right'; // свайп вправо з лівого краю
            }
            
            if (direction) {
                // Відправляємо повідомлення до батьківського вікна
                window.parent.postMessage({
                    type: 'swipe',
                    direction: direction
                }, window.location.origin);
            }
        }
    }
})();
