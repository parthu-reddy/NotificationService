document.addEventListener('DOMContentLoaded', () => {
    
    const form = document.getElementById('dispatch-form');
    const generateBtn = document.getElementById('btn-generate-uuid');
    const userIdInput = document.getElementById('userId');
    const submitBtn = document.getElementById('btn-submit');
    const toastContainer = document.getElementById('toast-container');

    // Auto-generate UUID on load
    userIdInput.value = crypto.randomUUID();

    // Generate UUID on button click
    generateBtn.addEventListener('click', () => {
        userIdInput.value = crypto.randomUUID();
        // Add a tiny micro-animation to the button
        generateBtn.style.transform = 'scale(0.9)';
        setTimeout(() => generateBtn.style.transform = 'scale(1)', 100);
    });

    // Dynamic field updates based on Channel selection
    const channelSelect = document.getElementById('channel');
    const explicitRecipientInput = document.getElementById('explicitRecipient');
    const explicitRecipientHelp = explicitRecipientInput.nextElementSibling;
    
    channelSelect.addEventListener('change', (e) => {
        const val = e.target.value;
        if (val === 'EMAIL') {
            explicitRecipientInput.placeholder = 'e.g. user@example.com';
            explicitRecipientHelp.textContent = 'Required for EMAIL';
            explicitRecipientInput.required = true;
        } else if (val === 'SMS') {
            explicitRecipientInput.placeholder = 'e.g. +1234567890';
            explicitRecipientHelp.textContent = 'Required for SMS';
            explicitRecipientInput.required = true;
        } else if (val === 'WHATSAPP') {
            explicitRecipientInput.placeholder = 'e.g. +1234567890';
            explicitRecipientHelp.textContent = 'Required for WHATSAPP';
            explicitRecipientInput.required = true;
        } else if (val === 'PUSH') {
            explicitRecipientInput.placeholder = 'e.g. device_token_abc123 (Optional)';
            explicitRecipientHelp.textContent = 'Optional for PUSH if user has devices registered';
            explicitRecipientInput.required = false;
        }
    });

    // Trigger once on load to set initial state
    channelSelect.dispatchEvent(new Event('change'));

    // Form submission
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        
        // UI Loading state
        submitBtn.classList.add('loading');
        submitBtn.disabled = true;

        const formData = new FormData(form);
        
        // Parse template params
        let parsedParams = [];
        const rawParams = formData.get('templateParams');
        if (rawParams && rawParams.trim() !== '') {
            try {
                // Check if it's JSON
                if (rawParams.trim().startsWith('[')) {
                    parsedParams = JSON.parse(rawParams);
                } else {
                    // Treat as comma separated
                    parsedParams = rawParams.split(',').map(s => s.trim());
                }
            } catch (err) {
                showToast('Failed to parse Template Parameters. Ensure it is valid JSON or comma-separated.', 'error');
                resetBtn();
                return;
            }
        }

        const payload = {
            userId: formData.get('userId'),
            channel: formData.get('channel'),
            eventName: formData.get('eventName'),
            explicitRecipient: formData.get('explicitRecipient') || null,
            templateParams: parsedParams
        };

        try {
            const response = await fetch('/api/test/notifications', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(payload)
            });

            const text = await response.text();

            if (response.ok) {
                showToast('Event dispatched to Kafka successfully!', 'success');
                // Optional: clear some fields
                // document.getElementById('explicitRecipient').value = '';
            } else {
                showToast(`Error: ${text || response.statusText}`, 'error');
            }
        } catch (error) {
            showToast(`Network Error: ${error.message}`, 'error');
        } finally {
            resetBtn();
        }
    });

    function resetBtn() {
        submitBtn.classList.remove('loading');
        submitBtn.disabled = false;
    }

    function showToast(message, type = 'success') {
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        
        const icon = type === 'success' 
            ? `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg>`
            : `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>`;

        toast.innerHTML = `
            ${icon}
            <span>${message}</span>
        `;
        
        toastContainer.appendChild(toast);

        // Trigger animation
        setTimeout(() => toast.classList.add('show'), 10);

        // Remove after 4 seconds
        setTimeout(() => {
            toast.classList.remove('show');
            setTimeout(() => toast.remove(), 300);
        }, 4000);
    }
});
