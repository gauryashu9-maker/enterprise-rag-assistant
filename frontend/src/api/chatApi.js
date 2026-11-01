import api from './axiosClient.js'
import { createChatApi } from './chatApiFactory.js'

export { errorMessage } from './chatApiFactory.js'

export default createChatApi(api)
