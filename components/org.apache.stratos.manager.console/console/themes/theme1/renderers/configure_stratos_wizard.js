var render = function (theme, data, meta, require) {
    session.put("configuring","false");
    var deploy_status = session.get("deploy-status");
    var list_status = session.get("get-status");
    var err_message = "";
    var title;
    var isErr = false;
    var wizard_on_val = [];
    for(var i=0; i<6 ;i++){
        if(i <= data.wizard.step-1){
            wizard_on_val.push(true);
        }else{
            wizard_on_val.push(false);
        }
    }
    if((deploy_status != null && deploy_status == "succeeded") && (list_status != null && list_status == "succeeded")) {
       isErr = false;
    } else if((deploy_status != null && !(deploy_status == "succeeded")) && (list_status != null && !(list_status == "succeeded"))) {
       isErr = true;
       step_data = "[]";
        err_message = deploy_status + " and " + list_status;
    } else if((deploy_status != null && deploy_status == "succeeded") && (list_status != null && !(list_status == "succeeded"))) {
        isErr = true;
        err_message = list_status;
        step_data = "[]";
    } else if((deploy_status != null && !(deploy_status == "succeeded")) && (list_status != null &&  list_status == "succeeded")) {
       isErr = true;
       err_message = deploy_status;
    }

    session.remove("get-status");
    session.remove("deploy-status");

    var config_status = data.wizard;
    if( config_status.step == 1 ){
        title = 'Partition Deployment';
    }else if( config_status.step == 2 ){
        title = 'Auto scale Policy Deployment';
    }else if( config_status.step == 3 ){
        title = 'Deployment Policy Deployment';
    }else if( config_status.step == 4 ){
        title = 'Lb';
    }else if( config_status.step == 5 ){
        title = 'Cartridge Deployment';
    }else if( config_status.step == 6 ){
        title = 'Multi-Tenant Service Deployment';
    }
    for(var i=0;i<step_data.length;i++){
        step_data[i].json_string = stringify(step_data[i]);
    }
    theme('index', {
        body: [
            {
                partial: 'configure_stratos_wizard',
                context: {
                    title:title,
                    step_data:data.step_data,
                    step:config_status.step,
                    wizard_on:true,
                    wizard_on_1:wizard_on_val[0],
                    wizard_on_2:wizard_on_val[1],
                    wizard_on_3:wizard_on_val[2],
                    wizard_on_4:wizard_on_val[3],
                    wizard_on_5:wizard_on_val[4],
                    wizard_on_6:wizard_on_val[5],
                    data_string:stringify(data.step_data)
                }
            }
        ],
        header: [
            {
                partial: 'header',
                context:{
                    title:'Configure Stratos',
                    button:{
                        link:'/',
                        name:'Deploy New Cartridge',
                        class_name:''
                    },
                    has_help:false,
                    step_data:true,
                    config_status:data.config_status,
                    wizard_on:true,
                    wizard_on_1:wizard_on_val[0],
                    wizard_on_2:wizard_on_val[1],
                    wizard_on_3:wizard_on_val[2],
                    wizard_on_4:wizard_on_val[3],
                    wizard_on_5:wizard_on_val[4],
                    wizard_on_6:wizard_on_val[5],
                    step:step,
                    configure_stratos:true,
                    error:isErr,
                    error_msg:err_message
                }
            }
        ],
        title:[
            {
                partial:'title',
                context:{
                    title:title
                }
            }
        ]
    });
};