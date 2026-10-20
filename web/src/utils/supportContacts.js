import telegramQr from '@/assets/contact/telegram-qr.jpg';
import whatsappQr from '@/assets/contact/whatsapp-qr.jpg';

export const SUPPORT_CONTACTS = Object.freeze({
  whatsapp: Object.freeze({
    href: 'https://wa.me/qr/DY3NG2HEGJFGL1',
    handle: 'WhatsApp',
    qrImage: whatsappQr
  }),
  telegram: Object.freeze({
    href: 'https://t.me/wybaby168',
    handle: '@wybaby168',
    qrImage: telegramQr
  })
});
