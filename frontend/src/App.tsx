import { FileDown, Sparkles, Upload } from 'lucide-react';

export function App() {
  return (
    <div className="app-shell">
      <header className="app-header">
        <div>
          <h1 className="brand-title">公文助手</h1>
          <p className="brand-subtitle">通知 / 请示 / 报告起草工作台</p>
        </div>
        <div className="header-actions">
          <button className="btn secondary" type="button">
            <Sparkles aria-hidden="true" className="btn-icon" />
            生成提纲
          </button>
          <button className="btn" type="button">
            <FileDown aria-hidden="true" className="btn-icon" />
            导出 Word
          </button>
        </div>
      </header>

      <main className="workbench">
        <section className="panel" aria-label="起草信息">
          <div className="panel-header">
            <h2 className="panel-title">文种、模板与材料</h2>
            <p className="panel-kicker">当前草稿：通知</p>
          </div>
          <div className="panel-body">
            <label className="field-group">
              <span className="field-label">文种</span>
              <select className="field" aria-label="文种" defaultValue="通知">
                <option>通知</option>
                <option>请示</option>
                <option>报告</option>
              </select>
            </label>

            <label className="field-group">
              <span className="field-label">标题</span>
              <input className="field" aria-label="标题" defaultValue="关于开展年度档案整理工作的通知" />
            </label>

            <label className="field-group">
              <span className="field-label">事项背景</span>
              <textarea className="field" aria-label="事项背景" defaultValue="年度资料归档不完整，需要统一整理。" />
            </label>

            <button className="btn secondary" type="button">
              <Upload aria-hidden="true" className="btn-icon" />
              上传 Word/PDF 材料
            </button>
          </div>
        </section>

        <section aria-label="公文预览">
          <div className="document-stage">
            <article className="document-paper">
              <h2 className="document-title">关于开展年度档案整理工作的通知</h2>
              <p>各部门、各直属单位：</p>
              <p>
                为进一步规范年度档案管理工作，提升资料归集、整理和归档质量，现就开展年度档案整理工作有关事项通知如下。
              </p>
              <p>
                <strong>一、整理范围</strong>
                <br />
                各部门在本年度形成的会议材料、制度文件、项目资料、台账记录及其他应归档资料。
              </p>
              <p>
                <strong>二、工作要求</strong>
                <br />
                各部门应指定专人负责，按照统一目录完成资料整理，确保材料完整、分类准确、命名规范。
              </p>
              <p className="signature">
                办公室
                <br />
                2026年5月25日
              </p>
            </article>
          </div>
        </section>

        <section className="panel" aria-label="AI 建议和质检">
          <div className="panel-header">
            <h2 className="panel-title">AI 建议与质检</h2>
            <p className="panel-kicker">基础检查 3 项</p>
          </div>
          <div className="panel-body">
            <div className="check-item success">基础字段完整</div>
            <div className="check-item warning">建议补充验收标准</div>
            <div className="check-item warning">建议引用档案管理制度</div>
            <button className="btn secondary" type="button">
              <Sparkles aria-hidden="true" className="btn-icon" />
              优化选中段落
            </button>
          </div>
        </section>
      </main>
    </div>
  );
}
