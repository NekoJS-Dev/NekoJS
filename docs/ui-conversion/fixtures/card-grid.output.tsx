// AI-assisted web conversion output — ticket 46 conversion fixture.
// Source: card-grid.html. Mapping decisions and downgrades: card-grid.conversion-report.json.
// Executed by ui-authoring-docs-proof.tsx (TypeScriptUiAuthoringDocsTest).
import { UI } from 'nekojs/jsx-runtime';

interface Product {
  id: string;
  title: string;
  description: string;
  price: string;
}

interface CardProps {
  product: Product;
}

// Web JS-generated card data becomes an explicit store; hover highlight becomes
// an explicit selected signal driven by onClick (there are no hover events).
const products: Product[] = [
  { id: 'p1', title: '星辉矿石', description: '闪烁着星光的稀有矿石。', price: '64' },
  { id: 'p2', title: '月光布匹', description: '在月光下织成的柔软布料。', price: '32' },
  { id: 'p3', title: '赤焰之核', description: '仍然温热的火焰核心。', price: '128' },
  { id: 'p4', title: '深渊珍珠', description: '来自深渊的黑色珍珠。', price: '96' },
  { id: 'p5', title: '风语羽毛', description: '携带低语的轻羽。', price: '16' },
  { id: 'p6', title: '磐石护符', description: '雕刻着符文的护符。', price: '48' }
];

export const catalog = UI.createStore({ products });
export const selected = UI.createSignal('');

function Card(props: CardProps) {
  const product = props.product;
  const isSelected = selected.get() === product.id;
  // Web image placeholder (no asset provided) maps to a placeholder panel — see report U-2.
  return (
    <panel id={'card-' + product.id} width="fill" padding={8} gap={4}
           background={isSelected ? '#E6F0FFFF' : '#FFFFFFFF'}
           borderColor={isSelected ? '#007BFFFF' : '#DDDDDDFF'} borderWidth={1} radius={4}>
      <panel id={'card-art-' + product.id} height={24} align="center" justify="center"
             background="#DDDDDDFF" radius={2}>
        <label id={'card-art-text-' + product.id} color="#999999FF" fontSize={8}>{'图片'}</label>
      </panel>
      <label id={'card-title-' + product.id} fontSize={{ base: 9, profiles: { 6: 10 } }}>{product.title}</label>
      <label id={'card-desc-' + product.id} color="#666666FF" fontSize={8}>{product.description}</label>
      <row id={'card-footer-' + product.id} justify="spaceBetween" align="center">
        <label id={'card-price-' + product.id} color="#007BFFFF" fontSize={10}>{product.price}</label>
        <button id={'card-buy-' + product.id}
                onClick={() => selected.set(product.id)}>{'购买'}</button>
      </row>
    </panel>
  );
}

export function renderCardGrid() {
  const items = catalog.get('products');
  const rows = [];
  for (let index = 0; index < items.length; index += 3) {
    // Authoring constraint: an uppercase component tag inside a {...} expression child does not
    // survive the current JSX lowering; UI.element(Component, props, key) is the supported form
    // there (same pattern as the ui-core.tsx proof).
    rows.push(
      <row key={'row-' + index} id={'grid-row-' + index} gap={8}>
        {items.slice(index, index + 3).map(item => UI.element(Card, { product: item }, item.id))}
      </row>
    );
  }
  return (
    <screen id="grid-screen" title="商品列表" pausesGame={false} closeOnEscape={true}>
      <scroll id="grid-scroll" width="100%" height="100%" scrollY={true}>
        <column id="grid-body" width="100%" padding={8} gap={8}>
          <label id="grid-title" fontSize={{ base: 10, profiles: { 6: 12 } }}>{'商品列表'}</label>
          <label id="grid-subtitle" color="gray" fontSize={8}>{'浏览我们的精选商品'}</label>
          {rows}
        </column>
      </scroll>
    </screen>
  );
}

export const conversion = Object.freeze({
  source: 'card-grid.html',
  output: 'card-grid.output.tsx',
  report: 'card-grid.conversion-report.json'
});
