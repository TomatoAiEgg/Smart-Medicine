import { DownloadOutlined, EditOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import {
  Alert,
  App,
  Button,
  Drawer,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Switch,
  Table,
  type FormItemProps,
  type FormInstance,
  type TableColumnsType,
} from 'antd';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import type { CommonListQuery, PageResult } from '../api/management.types';
import { downloadCsv } from '../utils/downloadCsv';
import { QueryTableShell } from './QueryTableShell';

export interface EntityRecord {
  id: string;
}

export interface SelectOption {
  label: string;
  value: string | boolean | number;
  disabled?: boolean;
}

export interface EntityFormField<TForm extends object> {
  name: Extract<keyof TForm, string>;
  label: string;
  kind?: 'text' | 'textarea' | 'select' | 'multiselect' | 'switch' | 'number';
  placeholder?: string;
  options?: SelectOption[];
  required?: boolean;
  disabledWhenEditing?: boolean;
  wide?: boolean;
}

export interface CsvColumn<TRecord> {
  title: string;
  value: (record: TRecord) => string | number | boolean | null | undefined;
}

interface EntityListPageProps<TRecord extends EntityRecord, TForm extends object> {
  title: string;
  subtitle: string;
  entityName: string;
  columns: TableColumnsType<TRecord>;
  fields: EntityFormField<TForm>[];
  initialValues: TForm;
  valuesFromRecord: (record: TRecord) => TForm;
  load: (params: CommonListQuery) => Promise<PageResult<TRecord>>;
  create: (values: TForm) => Promise<unknown>;
  update: (id: string, values: TForm) => Promise<unknown>;
  canWrite: boolean;
  csvColumns?: CsvColumn<TRecord>[];
  extraFilters?: ReactNode;
  emptyText?: string;
  showEnabledFilter?: boolean;
  remove?: (id: string) => Promise<unknown>;
  canRemove?: (record: TRecord) => boolean;
  extraRowActions?: (record: TRecord) => ReactNode;
}

function fieldControl<TForm extends object>(
  field: EntityFormField<TForm>,
  editing: boolean,
) {
  const disabled = editing && field.disabledWhenEditing;
  if (field.kind === 'textarea') return <Input.TextArea rows={4} placeholder={field.placeholder} />;
  if (field.kind === 'select' || field.kind === 'multiselect') {
    return <Select mode={field.kind === 'multiselect' ? 'multiple' : undefined} options={field.options} placeholder={field.placeholder} disabled={disabled} showSearch optionFilterProp="label" />;
  }
  if (field.kind === 'switch') return <Switch checkedChildren="启用" unCheckedChildren="停用" />;
  if (field.kind === 'number') return <InputNumber min={0} precision={0} style={{ width: '100%' }} />;
  return <Input placeholder={field.placeholder} disabled={disabled} />;
}

function resetForm<TForm extends object>(form: FormInstance<TForm>, values: TForm) {
  form.resetFields();
  form.setFieldsValue(values);
}

export function EntityListPage<TRecord extends EntityRecord, TForm extends object>({
  title,
  subtitle,
  entityName,
  columns,
  fields,
  initialValues,
  valuesFromRecord,
  load,
  create,
  update,
  canWrite,
  csvColumns,
  extraFilters,
  emptyText,
  showEnabledFilter = true,
  remove,
  canRemove,
  extraRowActions,
}: EntityListPageProps<TRecord, TForm>) {
  const { message, modal } = App.useApp();
  const [form] = Form.useForm<TForm>();
  const [rows, setRows] = useState<TRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [keyword, setKeyword] = useState('');
  const [enabled, setEnabled] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<TRecord | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const result = await load({ keyword: keyword.trim(), enabled, page, pageSize });
      setRows(result.records);
      setTotal(result.total);
      if (result.page !== page) setPage(result.page);
      if (result.pageSize !== pageSize) setPageSize(result.pageSize);
    } catch (loadError) {
      setRows([]);
      setTotal(0);
      setError(loadError instanceof Error ? loadError.message : `${entityName}加载失败`);
    } finally {
      setLoading(false);
    }
  }, [enabled, entityName, keyword, load, page, pageSize]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const openCreate = () => {
    setEditingRecord(null);
    resetForm(form, initialValues);
    setDrawerOpen(true);
  };

  const openEdit = (record: TRecord) => {
    setEditingRecord(record);
    resetForm(form, valuesFromRecord(record));
    setDrawerOpen(true);
  };

  const save = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editingRecord) {
        await update(editingRecord.id, values);
        message.success(`${entityName}已更新`);
      } else {
        await create(values);
        message.success(`${entityName}已新增`);
      }
      setDrawerOpen(false);
      await refresh();
    } catch (saveError) {
      message.error(saveError instanceof Error ? saveError.message : `${entityName}保存失败`);
    } finally {
      setSaving(false);
    }
  };

  const tableColumns = useMemo<TableColumnsType<TRecord>>(
    () => [
      ...columns,
      {
        title: '操作',
        key: 'actions',
        fixed: 'right',
        width: remove || extraRowActions ? 220 : 88,
        render: (_, record) => (
          <Space size={0}>
            <Button type="link" size="small" icon={<EditOutlined />} disabled={!canWrite} onClick={() => openEdit(record)}>
              编辑
            </Button>
            {remove ? (
              <Button
                type="link"
                size="small"
                danger
                disabled={!canWrite || (canRemove ? !canRemove(record) : false)}
                onClick={() => modal.confirm({
                  title: `删除${entityName}`,
                  content: '删除后无法恢复，请确认该记录未被业务数据引用。',
                  okText: '确认删除',
                  okButtonProps: { danger: true },
                  onOk: async () => {
                    await remove(record.id);
                    message.success(`${entityName}已删除`);
                    await refresh();
                  },
                })}
              >
                删除
              </Button>
            ) : null}
            {extraRowActions?.(record)}
          </Space>
        ),
      },
    ],
    [canRemove, canWrite, columns, entityName, extraRowActions, message, modal, refresh, remove],
  );

  const exportRows = () => {
    if (!csvColumns || rows.length === 0) return;
    downloadCsv(
      `${title}-第${page}页.csv`,
      csvColumns.map((column) => column.title),
      rows.map((record) => csvColumns.map((column) => column.value(record))),
    );
  };

  return (
    <>
      <QueryTableShell
        title={title}
        subtitle={subtitle}
        actions={
          <>
            <Button type="primary" icon={<PlusOutlined />} disabled={!canWrite} onClick={openCreate}>
              新增{entityName}
            </Button>
            <Button icon={<DownloadOutlined />} disabled={!csvColumns || rows.length === 0} onClick={exportRows}>
              导出当前页
            </Button>
          </>
        }
        filters={
          <div className="entity-filters">
            <Input
              allowClear
              value={keyword}
              prefix={<SearchOutlined />}
              placeholder={`搜索${entityName}`}
              onChange={(event) => setKeyword(event.target.value)}
              onPressEnter={() => {
                setPage(1);
                void refresh();
              }}
            />
            {showEnabledFilter ? (
              <Select
                value={enabled}
                options={[
                  { label: '全部状态', value: '' },
                  { label: '已启用', value: 'true' },
                  { label: '已停用', value: 'false' },
                ]}
                onChange={(value) => {
                  setEnabled(value);
                  setPage(1);
                }}
              />
            ) : null}
            {extraFilters}
            <Space>
              <Button type="primary" icon={<SearchOutlined />} loading={loading} onClick={() => {
                setPage(1);
                void refresh();
              }}>
                查询
              </Button>
              <Button icon={<ReloadOutlined />} disabled={loading} onClick={() => void refresh()}>
                刷新
              </Button>
            </Space>
          </div>
        }
        table={
          <>
            {error ? <Alert className="entity-list__alert" type="error" showIcon message={error} action={<Button onClick={() => void refresh()}>重试</Button>} /> : null}
            <Table<TRecord>
              rowKey="id"
              size="middle"
              loading={loading}
              columns={tableColumns}
              dataSource={rows}
              scroll={{ x: 'max-content' }}
              locale={{ emptyText: emptyText ?? `暂无${entityName}数据` }}
              pagination={{
                current: page,
                pageSize,
                total,
                showSizeChanger: true,
                showTotal: (count) => `共 ${count} 条`,
                onChange: (nextPage, nextPageSize) => {
                  setPage(nextPageSize === pageSize ? nextPage : 1);
                  setPageSize(nextPageSize);
                },
              }}
            />
          </>
        }
      />
      <Drawer
        open={drawerOpen}
        title={editingRecord ? `编辑${entityName}` : `新增${entityName}`}
        width={560}
        destroyOnHidden
        onClose={() => setDrawerOpen(false)}
        extra={
          <Space>
            <Button onClick={() => setDrawerOpen(false)}>取消</Button>
            <Button type="primary" loading={saving} onClick={() => void save()}>
              保存
            </Button>
          </Space>
        }
      >
        <Form<TForm> form={form} layout="vertical" initialValues={initialValues} disabled={saving || !canWrite}>
          <div className="entity-form-grid">
            {fields.map((field) => (
              <Form.Item
                key={field.name}
                className={field.wide ? 'entity-form-grid__wide' : undefined}
                name={field.name as FormItemProps['name']}
                label={field.label}
                valuePropName={field.kind === 'switch' ? 'checked' : 'value'}
                rules={field.required ? [{ required: true, message: `请输入或选择${field.label}` }] : undefined}
              >
                {fieldControl(field, Boolean(editingRecord))}
              </Form.Item>
            ))}
          </div>
        </Form>
      </Drawer>
    </>
  );
}
