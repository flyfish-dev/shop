import { renderBool, renderDefault } from '@/components/DataTable/tool';

const booleans = [true, false];

export default function(props) {
  const { text, record, index, column, key, openEditor, closeEditor } = props;
  if (column.customRender) {
    return column.customRender(props);
  }
  if (column.dbType === 'boolean' || booleans.includes(text)) {
    return renderBool(props);
  }
  return renderDefault(props)
}
