const common = {
  'zh-CN': {
    brand: 'Flyfish Dev',
    brandSubtitle: '飞鱼开源工作室',
    backHome: '返回首页',
    lastUpdated: '最后更新',
    updatedAt: '2026 年 7 月 13 日',
    privacyLink: '隐私政策',
    termsLink: '服务条款',
    contactTitle: '联系我们',
    contactBody: '如需咨询隐私、账号、订单或授权问题，请登录后创建支持请求，也可通过网站页脚的官方联系方式与我们联系。',
    contactAction: '联系支持',
    copyright: 'Flyfish Open Source'
  },
  'en-US': {
    brand: 'Flyfish Dev',
    brandSubtitle: 'Flyfish Open Source',
    backHome: 'Back to home',
    lastUpdated: 'Last updated',
    updatedAt: 'July 13, 2026',
    privacyLink: 'Privacy Policy',
    termsLink: 'Terms of Service',
    contactTitle: 'Contact us',
    contactBody: 'For privacy, account, order, or licensing questions, create a support request after signing in or use the official contact channels shown in the site footer.',
    contactAction: 'Contact support',
    copyright: 'Flyfish Open Source'
  }
};

const documents = {
  'zh-CN': {
    privacy: {
      eyebrow: '隐私与数据保护',
      title: '隐私政策',
      intro: '我们遵循最少必要、目的明确和安全可控的原则处理个人信息。本政策说明 Flyfish Dev 在账号登录、数字商品交易、自动交付与客户支持过程中如何使用和保护数据。',
      sections: [
        {
          title: '适用范围与运营方',
          paragraphs: [
            '本政策适用于 flyfish.dev、dev.flyfish.group 及由 Flyfish Dev 提供的低代码工具、开发者小铺、账号认证、授权交付和支持服务。相关服务由飞鱼开源工作室（Flyfish Open Source）运营。',
            '第三方网站和服务受其各自的隐私政策约束。通过 Flyfish Dev 跳转到第三方服务时，请同时阅读对应平台的规则。'
          ]
        },
        {
          title: '我们处理的信息',
          bullets: [
            '账号与身份信息：邮箱、昵称、头像，以及 Google、Microsoft、GitHub、Gitee、Gitea 或微信提供的账号标识和必要的基础资料。',
            '交易与支付信息：订单、商品、金额、币种、优惠、支付状态和支付机构交易标识。银行卡号等完整支付凭据由 Stripe 或其他支付机构处理，Flyfish Dev 不保存完整卡号。',
            '交付与授权信息：Git 平台绑定账号、目标仓库、授权域名、合同确认记录、许可证及交付状态。',
            '资料与沟通内容：个人资料、工单、客服消息、上传的图片和附件，以及解决问题所需的上下文。',
            '设备与安全信息：IP 地址、浏览器和设备信息、访问时间、操作日志、错误记录和风险控制信号。'
          ]
        },
        {
          title: '处理目的与依据',
          bullets: [
            '创建和识别账号，完成第三方登录、账号绑定及登录安全校验。',
            '处理订单、支付确认、合同签署、数字内容和仓库权限的自动交付。',
            '提供工单、客服、通知、售后处理与服务质量改进。',
            '防止欺诈、滥用和未经授权的访问，保障平台、用户和开发者权益。',
            '履行适用法律法规要求，以及在取得同意后提供相应功能。'
          ]
        },
        {
          title: '共享与受托处理',
          paragraphs: [
            '我们不会出售个人信息。仅在完成服务所必需的范围内，向身份认证平台、支付机构、代码托管平台、邮件与消息服务商、云基础设施和安全服务商提供必要信息。',
            '这些服务商只能按照约定目的处理数据。我们也可能在法律要求、争议解决或保护用户及平台合法权益时披露必要信息。'
          ]
        },
        {
          title: '存储、保留与跨境处理',
          paragraphs: [
            '我们仅在实现本政策所述目的、履行合同、解决争议及满足财务或合规义务所需期间保留信息。到期后将删除、匿名化或依法隔离保存。',
            'Google、Microsoft、GitHub、Stripe 等服务可能在境外处理必要数据。使用这些服务时，数据处理地点及保护措施同时受对应服务商政策约束。'
          ]
        },
        {
          title: '你的权利与选择',
          bullets: [
            '访问和修改个人资料，管理已绑定的第三方账号。',
            '申请查询、更正、删除或导出与账号相关的信息，但法律要求保留的交易或安全记录除外。',
            '撤回可选授权或停止使用服务；撤回不会影响此前基于授权完成的处理。',
            '对自动交付、账号归一化或订单处理结果提出疑问并申请人工协助。'
          ]
        },
        {
          title: '安全措施',
          paragraphs: [
            '我们使用 HTTPS、访问控制、权限分离、回调验签、密钥隔离、日志审计和备份等措施保护信息。任何互联网服务都无法承诺绝对安全；如发现风险，我们将及时处置并按适用要求通知。'
          ]
        },
        {
          title: '政策更新',
          paragraphs: [
            '当产品能力、合作方或法律要求发生变化时，我们可能更新本政策。重大变化会通过页面提示、站内通知或其他适当方式告知，更新日期以本页标注为准。'
          ]
        }
      ]
    },
    terms: {
      eyebrow: '服务规则与责任边界',
      title: '服务条款',
      intro: '本条款适用于 Flyfish Dev 提供的开发工具、数字商品、商业授权、自动交付和支持服务。使用服务前，请阅读并理解以下规则。',
      sections: [
        {
          title: '接受条款与服务范围',
          paragraphs: [
            '访问、注册或使用 Flyfish Dev 即表示你同意本条款和隐私政策。若你代表组织使用服务，应确保已获得相应授权。',
            '服务包括但不限于低代码工具、文件预览相关产品、开发者数字商品、源码或仓库权限、部署与商业授权、支付、自动交付和客户支持。具体内容以商品页、订单及授权文件为准。'
          ]
        },
        {
          title: '账号与安全',
          bullets: [
            '请提供准确、可联系的信息，并妥善保护邮箱、第三方账号和登录链接。',
            '不得转让账号、冒用他人身份，或绕过认证、权限与风险控制措施。',
            '发现未经授权的访问时，请立即通过工单或官方联系方式告知我们。'
          ]
        },
        {
          title: '合理使用',
          bullets: [
            '不得利用服务实施违法活动、侵犯知识产权、传播恶意代码或干扰平台运行。',
            '不得未经授权抓取数据、探测漏洞、规避限额，或将许可证、交付文件和私有仓库权限提供给无权使用的主体。',
            '集成测试、在线运行和代码生成功能应仅处理你有权使用的数据和代码。'
          ]
        },
        {
          title: '订单、价格与支付',
          paragraphs: [
            '商品价格、币种、优惠和税费以提交订单时的页面及最终支付确认结果为准。支付由 Stripe、H5 支付或页面列明的其他支付机构处理。',
            '退款、取消和变更按照商品页说明、交付状态、双方约定及适用法律处理。数字内容、源码、许可证或仓库权限一旦完成交付，可能依法受到退款限制。'
          ]
        },
        {
          title: '交付、授权与合同',
          paragraphs: [
            '自动交付依赖你提供的 Git 平台账号、仓库信息、授权域名和其他必要资料。资料错误、第三方平台限制或账号未绑定可能导致交付延迟。',
            '开源项目继续适用其仓库中公布的开源许可证；商业版、企业版、部署授权及定制服务适用订单、合同和随交付文件提供的许可范围。除明确约定外，购买不转移软件著作权或商标权。'
          ]
        },
        {
          title: '第三方服务',
          paragraphs: [
            'Google、Microsoft、GitHub、Gitee、Gitea、微信、Stripe 等第三方服务由对应提供方运营。其可用性、账号限制和处理规则受各自条款约束，我们会在合理范围内协助处理集成问题。'
          ]
        },
        {
          title: '知识产权与品牌',
          paragraphs: [
            'Flyfish Dev 的品牌、界面、文档和非开源内容受适用知识产权法律保护。未经书面许可，不得以可能造成关联、背书或来源混淆的方式使用 Flyfish 名称、徽标或视觉资产。',
            '你保留对自行提交内容的合法权利，并授权我们仅为提供、保障和改进相关服务而处理这些内容。'
          ]
        },
        {
          title: '服务变更、暂停与终止',
          paragraphs: [
            '我们可能因维护、安全、法律要求或产品调整变更服务，并尽量降低对已购服务的影响。严重违反本条款、存在安全风险或长期欠费时，我们可限制或暂停相关能力。',
            '终止服务不影响终止前已经产生的付款、授权、保密、知识产权和争议处理义务。'
          ]
        },
        {
          title: '责任边界',
          paragraphs: [
            '我们将以合理的专业水平提供服务，但不对第三方平台中断、不可抗力、用户配置错误或超出约定用途的使用结果作不切实际的保证。',
            '在法律允许范围内，各方责任以直接、可合理预见的损失为限；法律规定不得限制的责任不受本条限制。'
          ]
        },
        {
          title: '条款更新与争议处理',
          paragraphs: [
            '我们可能因服务或法律变化更新本条款。重大变化会通过适当方式提示。争议应首先通过工单友好协商；除强制性法律另有规定外，本条款适用中华人民共和国法律。'
          ]
        }
      ]
    }
  },
  'en-US': {
    privacy: {
      eyebrow: 'Privacy and data protection',
      title: 'Privacy Policy',
      intro: 'We process personal data only when necessary, for clear purposes, and with appropriate safeguards. This policy explains how Flyfish Dev uses and protects data across sign-in, digital commerce, automated delivery, and customer support.',
      sections: [
        {
          title: 'Scope and operator',
          paragraphs: [
            'This policy applies to flyfish.dev, dev.flyfish.group, and the low-code tools, developer marketplace, account authentication, license delivery, and support services provided by Flyfish Dev. These services are operated by Flyfish Open Source.',
            'Third-party websites and services are governed by their own privacy policies. Please review those policies when Flyfish Dev directs you to an external provider.'
          ]
        },
        {
          title: 'Information we process',
          bullets: [
            'Account and identity data: email address, display name, avatar, provider account identifiers, and necessary basic profile data from Google, Microsoft, GitHub, Gitee, Gitea, or WeChat.',
            'Transaction and payment data: orders, products, amount, currency, discounts, payment status, and payment-provider transaction identifiers. Full card details are processed by Stripe or other payment providers and are not stored by Flyfish Dev.',
            'Delivery and licensing data: linked Git accounts, target repositories, licensed domains, contract acceptance records, license artifacts, and delivery status.',
            'Profile and communications: profile details, support tickets, customer-service messages, uploaded images and attachments, and context needed to resolve a request.',
            'Device and security data: IP address, browser and device information, access times, audit events, errors, and risk-control signals.'
          ]
        },
        {
          title: 'Why we process data',
          bullets: [
            'Create and identify accounts, complete third-party sign-in and account linking, and protect login security.',
            'Process orders, confirm payments and contracts, and deliver digital content, licenses, and repository access.',
            'Provide support tickets, customer service, notifications, after-sales support, and service improvements.',
            'Prevent fraud, abuse, and unauthorized access, and protect users, developers, and the platform.',
            'Meet applicable legal requirements and provide optional features with your consent.'
          ]
        },
        {
          title: 'Sharing and service providers',
          paragraphs: [
            'We do not sell personal data. We share only the information needed to provide the service with identity providers, payment processors, Git hosting platforms, email and messaging providers, cloud infrastructure, and security providers.',
            'These providers may process data only for the agreed purpose. We may also disclose necessary information when required by law, to resolve disputes, or to protect the lawful rights of users and the platform.'
          ]
        },
        {
          title: 'Storage, retention, and international processing',
          paragraphs: [
            'We retain data only as long as needed to provide the services described here, perform a contract, resolve disputes, and meet financial or compliance obligations. We then delete, anonymize, or lawfully isolate the data.',
            'Providers such as Google, Microsoft, GitHub, and Stripe may process necessary data outside your country. Their processing locations and safeguards are also governed by their respective policies.'
          ]
        },
        {
          title: 'Your rights and choices',
          bullets: [
            'Access and update your profile and manage linked third-party accounts.',
            'Request access, correction, deletion, or export of account-related data, except records we must retain for legal, transaction, or security reasons.',
            'Withdraw optional consent or stop using the service. Withdrawal does not affect processing already completed on a valid basis.',
            'Ask for human assistance with automated delivery, account matching, or order-processing outcomes.'
          ]
        },
        {
          title: 'Security',
          paragraphs: [
            'We use HTTPS, access controls, separation of duties, signed callback verification, secret isolation, audit logging, and backups. No internet service can promise absolute security; when a risk is identified, we will respond promptly and provide notice where required.'
          ]
        },
        {
          title: 'Policy changes',
          paragraphs: [
            'We may update this policy when our products, service providers, or legal requirements change. We will communicate material changes through the site, in-product notices, or another appropriate channel. The date shown on this page identifies the current version.'
          ]
        }
      ]
    },
    terms: {
      eyebrow: 'Service rules and responsibilities',
      title: 'Terms of Service',
      intro: 'These terms apply to Flyfish Dev developer tools, digital products, commercial licenses, automated delivery, and support services. Please read and understand them before using the service.',
      sections: [
        {
          title: 'Acceptance and service scope',
          paragraphs: [
            'By accessing, registering for, or using Flyfish Dev, you agree to these terms and the Privacy Policy. If you use the service for an organization, you confirm that you are authorized to do so.',
            'Services may include low-code tools, file-preview products, developer goods, source or repository access, deployment and commercial licenses, payments, automated delivery, and support. The product page, order, contract, and license files define the specific scope.'
          ]
        },
        {
          title: 'Accounts and security',
          bullets: [
            'Provide accurate, reachable information and protect your email, third-party accounts, and sign-in links.',
            'Do not transfer accounts, impersonate others, or bypass authentication, authorization, or risk controls.',
            'Notify us promptly through a support ticket or official contact channel if you discover unauthorized access.'
          ]
        },
        {
          title: 'Acceptable use',
          bullets: [
            'Do not use the service for unlawful activity, intellectual-property infringement, malware distribution, or disruption of the platform.',
            'Do not scrape data without authorization, probe vulnerabilities, evade limits, or share licenses, delivery files, or private repository access with unauthorized parties.',
            'Use integration testing, online runtime, and code-generation features only with data and code you are authorized to process.'
          ]
        },
        {
          title: 'Orders, pricing, and payments',
          paragraphs: [
            'Product price, currency, discounts, and taxes are determined by the order page and final payment confirmation. Payments are processed by Stripe, H5 payment, or another provider identified at checkout.',
            'Refunds, cancellations, and changes are handled according to the product description, delivery status, agreed terms, and applicable law. Delivered digital content, source code, licenses, or repository access may be subject to lawful refund restrictions.'
          ]
        },
        {
          title: 'Delivery, licenses, and contracts',
          paragraphs: [
            'Automated delivery depends on accurate Git account, repository, licensed-domain, and other required information. Incorrect details, third-party restrictions, or an unlinked account may delay delivery.',
            'Open-source projects remain governed by the licenses published in their repositories. Commercial, enterprise, deployment, and custom services are governed by the order, contract, and delivered license files. A purchase does not transfer software copyright or trademark rights unless expressly agreed.'
          ]
        },
        {
          title: 'Third-party services',
          paragraphs: [
            'Google, Microsoft, GitHub, Gitee, Gitea, WeChat, Stripe, and similar services are operated by their respective providers. Their availability, account restrictions, and processing rules are governed by their own terms. We will reasonably assist with integration issues.'
          ]
        },
        {
          title: 'Intellectual property and brand',
          paragraphs: [
            'The Flyfish Dev brand, interface, documentation, and non-open-source content are protected by applicable intellectual-property laws. Do not use the Flyfish name, logo, or visual assets in a way that implies an unauthorized affiliation, endorsement, or source.',
            'You retain lawful rights in content you submit and permit us to process it only as needed to provide, secure, and improve the relevant service.'
          ]
        },
        {
          title: 'Changes, suspension, and termination',
          paragraphs: [
            'We may change services for maintenance, security, legal, or product reasons and will seek to minimize the impact on purchased services. We may restrict or suspend capabilities for serious violations, security risks, or prolonged non-payment.',
            'Termination does not affect payment, licensing, confidentiality, intellectual-property, or dispute obligations that arose before termination.'
          ]
        },
        {
          title: 'Liability boundaries',
          paragraphs: [
            'We provide services with reasonable professional care, but do not make unrealistic guarantees regarding third-party outages, force majeure, user configuration errors, or use beyond the agreed scope.',
            'To the extent permitted by law, liability is limited to direct and reasonably foreseeable loss. Liability that cannot lawfully be limited remains unaffected.'
          ]
        },
        {
          title: 'Updates and dispute resolution',
          paragraphs: [
            'We may update these terms as services or laws change and will communicate material changes appropriately. Disputes should first be addressed in good faith through a support ticket. Unless mandatory law requires otherwise, these terms are governed by the laws of the People\'s Republic of China.'
          ]
        }
      ]
    }
  }
};

export const getLegalDocument = (locale, type) => ({
  ...common[locale],
  ...documents[locale][type]
});
