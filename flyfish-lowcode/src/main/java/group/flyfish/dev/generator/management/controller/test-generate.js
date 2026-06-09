
async function generate() {
  const response = await fetch('http://localhost:10081/integrity/codes', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      'author': 'flyfish',
      'auto': true,
      'commentDate': 'yyyy-MM-dd',
      'dateType': 'ONLY_DATE',
      'disableOpenDir': true,
      'enableKotlin': false,
      'enableSwagger': false,
      'excludes': [],
      'fileOverride': true,
      'includes': [
        'demo_order',
        'demo_order_item'
      ],
      'r2dbc': {
        'password': '',
        'url': 'r2dbc:mysql://127.0.0.1:3306/flyfish_dev',
        'username': 'root'
      },
      'outputDir': './dt-generator/src/generated',
      'packageConfig': {
        'controller': 'controller',
        'entity': 'domain.po',
        'mapper': 'mapper',
        'moduleName': '',
        'other': 'other',
        'parent': 'group.flyfish.generated',
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
