
async function generate() {
  const response = await fetch('http://localhost:10081/integrity/codes', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      'author': 'wangyu',
      'auto': true,
      'commentDate': 'yyyy-MM-dd',
      'dateType': 'ONLY_DATE',
      'disableOpenDir': true,
      'enableKotlin': false,
      'enableSwagger': false,
      'excludes': [],
      'fileOverride': true,
      'includes': [
        't_app_foster_instance',
        't_app_foster_history'
      ],
      'r2dbc': {
        'password': 'change-me-password',
        'url': 'r2dbc:mysql://main-mysqls.mysql.database.azure.com:3306/dt_project?sslMode=REQUIRED&serverZoneId=Asia/Shanghai',
        'username': 'root1'
      },
      'outputDir': './dt-generator/src/generated',
      'packageConfig': {
        'controller': 'controller',
        'entity': 'domain.po',
        'mapper': 'mapper',
        'moduleName': '',
        'other': 'other',
        'parent': 'com.chinaunicom.system.dt.modular.biz.foster',
        'pathInfo': {},
        'service': 'service',
        'serviceImpl': 'service.impl',
        'xml': 'mapper.mapping'
      },
      'tablePrefix': [
        't_dl_',
        'sys_',
        't_'
      ],
      'tableSuffix': []
    })
  });
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a')
  a.href = url
  a.download = '代码.zip';
  a.click()
  window.URL.revokeObjectURL(url)
}
