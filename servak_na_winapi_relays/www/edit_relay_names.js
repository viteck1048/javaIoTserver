// Функція для створення елемента релейного виходу
function createRelayItem(number, name) {
    const item = document.createElement('div');
    item.className = 'relay-item';
    item.innerHTML = `
        <div class="relay-number">${number}</div>
        <div class="relay-name">${name}</div>
        <button class="edit-btn" onclick="editRelay(${number})">Редагувати</button>
    `;
    return item;
}

// Функція для відкриття модального вікна
function editRelay(number) {
    const modal = document.getElementById('editModal');
    const form = document.getElementById('editForm');
    const relayNumber = document.getElementById('relayNumber');
    const relayName = document.getElementById('relayName');

    relayNumber.value = number;
    relayName.value = '';
    
    modal.style.display = 'block';

    // Закриття модального вікна при кліку на крестик
    const closeModal = document.querySelector('.close-modal');
    closeModal.onclick = () => {
        modal.style.display = 'none';
    }

    // Закриття модального вікна при кліку на підкладку
    window.onclick = (event) => {
        if (event.target === modal) {
            modal.style.display = 'none';
        }
    }

    // Обробка форми
    form.onsubmit = async (event) => {
        event.preventDefault();
        
        const newName = relayName.value.trim();
        if (!newName) return;

        try {
            // Відправляємо запит на сервер для зміни імені
            const response = await fetch(`set_relay_name?relay=${number}&name=${encodeURIComponent(newName)}`, {
                method: 'POST'
            });

            if (response.ok) {
                // Оновлюємо список
                await loadRelays(sn);
                modal.style.display = 'none';
            } else {
                alert('Помилка при збереженні імені');
            }
        } catch (error) {
            console.error('Error:', error);
            alert('Помилка при збереженні імені');
        }
    };
}

// Функція для завантаження списку релейних виходів
async function loadRelays(sn) {
    try {
        console.log('Loading relays for device ' + sn);
        const response = await fetch('get_relays?sn=' + sn);
        console.log('Response status:', response.status);
        
        if (!response.ok) {
            throw new Error('Network response was not ok');
        }
        
        const data = await response.json();
        console.log('Received data:', data);
        
        // Set device name in input field
        const deviceNameInput = document.getElementById('deviceName');
        if (deviceNameInput) {
            deviceNameInput.value = data.gadget_name || '';
        }
        
        // Update log link
        const logLink = document.getElementById('logLink');
        if (logLink) {
            logLink.href = `view_log_order.html?command=log_file&sn=${data.sn}`;
        }
        
        const relayList = document.getElementById('relayList');
        relayList.innerHTML = '';

        // Add relay list
        data.relays.forEach((relay, index) => {
            const relayDiv = document.createElement('div');
            relayDiv.className = 'relay-item';
            relayDiv.innerHTML = `
                <div class="relay-number">Relay${index + 1}</div>
                <input type="text" class="relay-name" name="relay${index + 1}" value="${relay.name}">
            `;
            relayList.appendChild(relayDiv);
        });
    } catch (error) {
        console.error('Error:', error);
        alert('Помилка при завантаженні списку релейних виходів');
    }
}

// Завантажуємо список при завантаженні сторінки
document.addEventListener('DOMContentLoaded', function() {
    const urlParams = new URLSearchParams(window.location.search);
    const sn = urlParams.get('sn');
    if (sn) {
        loadRelays(sn);
    }

    // Додаємо обробник для форми зміни імені пристрою
    const deviceNameForm = document.getElementById('deviceNameForm');
    if (deviceNameForm) {
        deviceNameForm.addEventListener('submit', async function(e) {
            e.preventDefault();
            const newName = document.getElementById('deviceName').value.trim();
            if (newName) {
                try {
                    // Формуємо URL-encoded рядок
                    const params = new URLSearchParams();
                    params.append('sn', sn);
                    params.append('name', newName);
                    
                    const response = await fetch('set_relay_name', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/x-www-form-urlencoded'
                        },
                        body: params.toString()
                    });
                    
                    if (response.ok) {
                        // Оновлюємо сторінку
                        //window.location.reload();
                    } else {
                        alert('Error changing name');
                    }
                } catch (error) {
                    console.error('Error:', error);
                    alert('Error changing name');
                }
            }
        });
    }

    // Додаємо обробник для форми зміни імені виходів
    const relayForm = document.getElementById('relayForm');
    if (relayForm) {
        relayForm.addEventListener('submit', async function(e) {
            e.preventDefault();
            
            try {
                // Отримуємо всі значення з форми
                const formData = new FormData(relayForm);
                const params = new URLSearchParams();
                
                // Додаємо всі поля з форми
                for (let pair of formData.entries()) {
                    params.append(pair[0], pair[1]);
                }
                // Додаємо sn
                params.append('sn', sn);
                
                const response = await fetch('set_relay_names', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded'
                    },
                    body: params.toString()
                });
                
                if (response.ok) {
                    // Оновлюємо сторінку
                    //window.location.reload();
                } else {
                    alert('Error saving relay names');
                }
            } catch (error) {
                console.error('Error:', error);
                alert('Error saving relay names');
            }
        });
    }
});
