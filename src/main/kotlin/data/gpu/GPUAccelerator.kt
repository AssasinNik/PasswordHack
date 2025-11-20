package data.gpu

import org.jocl.*

class GPUAccelerator {
    companion object {
        init {
            CL.setExceptionsEnabled(true)
        }
        
        private var initialized = false
        private var context: cl_context? = null
        private var device: cl_device_id? = null
        private var commandQueue: cl_command_queue? = null
        
        fun isAvailable(): Boolean {
            if (initialized) return context != null
            initialized = true
            
            try {
                val numPlatforms = IntArray(1)
                CL.clGetPlatformIDs(0, null, numPlatforms)
                
                if (numPlatforms[0] == 0) return false
                
                val platforms = arrayOfNulls<cl_platform_id>(numPlatforms[0])
                CL.clGetPlatformIDs(platforms.size, platforms, null)
                val platform = platforms[0] ?: return false
                
                val numDevices = IntArray(1)
                CL.clGetDeviceIDs(platform, CL.CL_DEVICE_TYPE_GPU, 0, null, numDevices)
                
                if (numDevices[0] == 0) return false
                
                val devices = arrayOfNulls<cl_device_id>(numDevices[0])
                CL.clGetDeviceIDs(platform, CL.CL_DEVICE_TYPE_GPU, devices.size, devices, null)
                device = devices[0] ?: return false
                
                val contextProperties = cl_context_properties()
                contextProperties.addProperty(CL.CL_CONTEXT_PLATFORM.toLong(), platform)
                
                context = CL.clCreateContext(contextProperties, 1, arrayOf(device), null, null, null)
                commandQueue = CL.clCreateCommandQueue(context, device, 0, null)
                
                return true
            } catch (e: Exception) {
                return false
            }
        }
        
        fun cleanup() {
            try {
                commandQueue?.let { CL.clReleaseCommandQueue(it) }
                context?.let { CL.clReleaseContext(it) }
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
            commandQueue = null
            context = null
            device = null
        }
    }
}

