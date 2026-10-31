import DefaultTheme from 'vitepress/theme'
import DiagramLayout from './DiagramLayout.vue'
import './custom.css'

export default {
  extends: DefaultTheme,
  Layout: DiagramLayout,
}
