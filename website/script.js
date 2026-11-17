/**
 * AirAssistant Website Interactive Script
 */

document.addEventListener('DOMContentLoaded', () => {
  // Configuration: Update this with your hosted direct APK link (e.g. GitHub Releases URL)
  // or keep it relative for self-hosted setup.
  const CONFIG = {
    appName: 'AirAssistant',
    version: '1.0.0',
    // Default APK download path (can be relative or a GitHub release URL)
    apkFilename: 'AirAssistant-v1.0.0.apk',
    downloadPath: './downloads/AirAssistant-v1.0.0.apk',
    // Fallback if hosted on GitHub (Ahmer's official GitHub repo)
    githubReleasesUrl: 'https://github.com/Ahmerkhan12/Air-Assistant/releases/latest'
  };

  // Determine absolute download URL
  const currentOrigin = window.location.origin + window.location.pathname.replace(/\/index\.html$/, '');
  const absoluteDownloadUrl = CONFIG.downloadPath.startsWith('http') 
    ? CONFIG.downloadPath 
    : (window.location.protocol === 'file:' 
        ? CONFIG.apkFilename 
        : `${currentOrigin.replace(/\/$/, '')}/${CONFIG.downloadPath.replace(/^\.\//, '')}`);

  // Setup Download Links
  const primaryDownloadBtn = document.getElementById('primary-download-btn');
  const triggerDownloadBtns = document.querySelectorAll('.trigger-download');
  const apkUrlInput = document.getElementById('apk-direct-url');

  if (primaryDownloadBtn) {
    primaryDownloadBtn.href = CONFIG.downloadPath;
    primaryDownloadBtn.setAttribute('download', CONFIG.apkFilename);
  }

  triggerDownloadBtns.forEach(btn => {
    btn.href = CONFIG.downloadPath;
    btn.setAttribute('download', CONFIG.apkFilename);
  });

  if (apkUrlInput) {
    apkUrlInput.value = window.location.protocol === 'file:' 
      ? 'https://your-domain.com/downloads/' + CONFIG.apkFilename 
      : absoluteDownloadUrl;
  }

  // Device detection: Customize download prompt for Android users
  const isAndroid = /android/i.test(navigator.userAgent);
  if (isAndroid && primaryDownloadBtn) {
    const mainText = primaryDownloadBtn.querySelector('.btn-main-text');
    if (mainText) {
      mainText.textContent = 'Install APK on this Device';
    }
  }

  // QR Code Generation
  const qrContainer = document.getElementById('qrcode');
  if (qrContainer && typeof QRCode !== 'undefined') {
    const qrTargetUrl = window.location.protocol === 'file:' 
      ? 'https://github.com/Ahmerkhan12/Android_app/releases/latest' 
      : absoluteDownloadUrl;

    new QRCode(qrContainer, {
      text: qrTargetUrl,
      width: 180,
      height: 180,
      colorDark: '#07090e',
      colorLight: '#ffffff',
      correctLevel: QRCode.CorrectLevel.H
    });
  }

  // Modal logic
  const showQrBtn = document.getElementById('show-qr-btn');
  const qrModal = document.getElementById('qr-modal');
  const closeModalBtn = document.getElementById('close-modal-btn');
  const copyUrlBtn = document.getElementById('copy-url-btn');

  if (showQrBtn && qrModal) {
    showQrBtn.addEventListener('click', () => {
      qrModal.classList.add('open');
      document.body.style.overflow = 'hidden';
    });
  }

  const closeModal = () => {
    if (qrModal) {
      qrModal.classList.remove('open');
      document.body.style.overflow = '';
    }
  };

  if (closeModalBtn) {
    closeModalBtn.addEventListener('click', closeModal);
  }

  if (qrModal) {
    qrModal.addEventListener('click', (e) => {
      if (e.target === qrModal) {
        closeModal();
      }
    });
  }

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && qrModal && qrModal.classList.contains('open')) {
      closeModal();
    }
  });

  // Copy Link Handler
  if (copyUrlBtn && apkUrlInput) {
    copyUrlBtn.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(apkUrlInput.value);
        const originalText = copyUrlBtn.textContent;
        copyUrlBtn.textContent = 'Copied!';
        copyUrlBtn.style.background = 'rgba(16, 185, 129, 0.2)';
        copyUrlBtn.style.color = '#10b981';

        setTimeout(() => {
          copyUrlBtn.textContent = originalText;
          copyUrlBtn.style.background = '';
          copyUrlBtn.style.color = '';
        }, 2000);
      } catch (err) {
        apkUrlInput.select();
        document.execCommand('copy');
      }
    });
  }

  // FAQ Accordion
  const accordionItems = document.querySelectorAll('.accordion-item');
  accordionItems.forEach(item => {
    const header = item.querySelector('.accordion-header');
    const content = item.querySelector('.accordion-content');

    header.addEventListener('click', () => {
      const isActive = item.classList.contains('active');

      // Close other open accordions
      accordionItems.forEach(otherItem => {
        if (otherItem !== item) {
          otherItem.classList.remove('active');
          const otherContent = otherItem.querySelector('.accordion-content');
          if (otherContent) otherContent.style.maxHeight = null;
        }
      });

      // Toggle current
      if (!isActive) {
        item.classList.add('active');
        content.style.maxHeight = content.scrollHeight + 'px';
      } else {
        item.classList.remove('active');
        content.style.maxHeight = null;
      }
    });
  });
});

